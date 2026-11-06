package com.yk.notiffilter.service

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.yk.notiffilter.classify.ClassifyRequest
import com.yk.notiffilter.classify.Decision
import com.yk.notiffilter.classify.NotificationClassifier
import com.yk.notiffilter.classify.Tokenizer
import com.yk.notiffilter.data.AppRule
import com.yk.notiffilter.data.FilterMode
import com.yk.notiffilter.data.LogAction
import com.yk.notiffilter.data.LogEntry
import com.yk.notiffilter.data.MatchTarget
import com.yk.notiffilter.data.NotifFilterDatabase
import com.yk.notiffilter.data.NotificationRule
import com.yk.notiffilter.data.TemplateVerdict
import com.yk.notiffilter.data.Verdict
import com.yk.notiffilter.data.VerdictSource
import com.yk.notiffilter.fingerprint.Fingerprinter
import com.yk.notiffilter.settings.AppSettings

/** What the engine needs from the platform. Keeps the decision logic off the service. */
interface FilterHost {
    fun cancel(key: String)
    fun repost(sbn: StatusBarNotification, appLabel: String, title: String, text: String)

    /** Silent, low-priority summary of how many templates are waiting for an answer. */
    fun nudge(pendingCount: Int, appLabels: List<String>)
    fun clearNudge()
}

/**
 * The live path.
 *
 * Two rules govern everything here.
 *
 * *Optimistic show*: a notification stays on screen until something positively
 * decides to hide it. Every failure — dead listener, untrained model, process killed
 * mid-flight — degrades to "the user sees the notification", which is the behaviour
 * without this app installed.
 *
 * *Never hide on a hunch*: the classifier may act alone only when the evidence clears
 * a margin. Anything short of that is queued for the user, and the queue is what the
 * model actually learns from. The user is the authority; the model is a labour-saving
 * device that fills in the templates it can already recognise.
 */
class FilterEngine(
    private val db: NotifFilterDatabase,
    /** Built per call so a threshold change in Settings applies to the next notification. */
    private val classifierFor: () -> NotificationClassifier,
    private val settings: AppSettings? = null,
) {

    /** Extracted so the decision can be unit-tested without a StatusBarNotification. */
    data class Incoming(
        val packageName: String,
        val key: String,
        val channelId: String?,
        val title: String,
        val text: String,
        /**
         * True when our re-post could not reproduce this notification faithfully.
         *
         * Our copy is plain title + text on our own channel. Anything carrying
         * actions (inline reply, "copy OTP"), MessagingStyle, a picture, media
         * controls or a progress bar loses all of it in translation — and since
         * re-posting cancels the original, that loss is permanent. Such a
         * notification is kept by leaving it entirely alone.
         */
        val richContent: Boolean = false,
        val isOngoing: Boolean = false,
    )

    suspend fun onPosted(sbn: StatusBarNotification, host: FilterHost) {
        val incoming = extract(sbn) ?: return
        process(incoming, host, sbn)
    }

    suspend fun process(
        incoming: Incoming,
        host: FilterHost,
        sbn: StatusBarNotification? = null,
    ) {
        val rule = db.appRules().enabledRule(incoming.packageName) ?: return

        val fingerprint = Fingerprinter.fingerprint(
            incoming.packageName,
            incoming.channelId,
            incoming.title,
            incoming.text,
        )

        // Tier 1: Pinned template from database (settled user answers only)
        val known = db.verdicts().find(fingerprint)
        if (known != null && known.source == VerdictSource.USER && !known.needsVerdict) {
            log(rule, incoming, known, fromCache = true)
            apply(known, rule, incoming, sbn, host)
            return
        }

        // Tier 2 & Tier 3: Deterministic Rule Matching (Whitelist KEEP first -> Blacklist HIDE second)
        val activeRules = db.notificationRules().getActiveRulesForApp(incoming.packageName)
        val ruleMatch = com.yk.notiffilter.rule.RuleMatcher.match(
            rules = activeRules,
            packageName = incoming.packageName,
            channelId = incoming.channelId,
            title = incoming.title,
            text = incoming.text,
        )

        if (ruleMatch.matched) {
            val templateVerdict = TemplateVerdict(
                fingerprint = fingerprint,
                packageName = incoming.packageName,
                verdict = ruleMatch.verdict,
                reason = ruleMatch.reason,
                source = VerdictSource.MODEL,
                needsVerdict = false,
                score = if (ruleMatch.verdict == Verdict.HIDE) 10.0 else -10.0,
                channelId = incoming.channelId.orEmpty(),
                sampleTitle = incoming.title,
                sampleText = incoming.text,
            )
            log(rule, incoming, templateVerdict, fromCache = false)
            apply(templateVerdict, rule, incoming, sbn, host)
            return
        }

        // Tier 1.5: Fuzzy template matching against settled user answers for this app
        val userSettledTemplates = db.verdicts().trainingForApp(incoming.packageName)
        val incomingFeatures = Tokenizer.features(incoming.channelId, incoming.title, incoming.text)
        val fuzzyMatch = findFuzzyMatch(incomingFeatures, userSettledTemplates)
        if (fuzzyMatch != null) {
            val fuzzyVerdict = TemplateVerdict(
                fingerprint = fingerprint,
                packageName = incoming.packageName,
                verdict = fuzzyMatch.verdict,
                reason = "Similar to: ${fuzzyMatch.sampleTitle.take(25)}",
                source = VerdictSource.USER,
                needsVerdict = false,
                score = if (fuzzyMatch.verdict == Verdict.HIDE) 8.0 else -8.0,
                channelId = incoming.channelId.orEmpty(),
                sampleTitle = incoming.title,
                sampleText = incoming.text,
            )
            db.verdicts().insertIfAbsent(fuzzyVerdict)
            log(rule, incoming, fuzzyVerdict, fromCache = true)
            apply(fuzzyVerdict, rule, incoming, sbn, host)
            return
        }

        // If an earlier unreviewed model verdict exists, use it
        if (known != null) {
            log(rule, incoming, known, fromCache = true)
            apply(known, rule, incoming, sbn, host)
            return
        }

        // Tier 4: Tuned Bayesian Classifier for unseen, unruled templates
        val classification = classifierFor().classify(
            ClassifyRequest(
                packageName = incoming.packageName,
                channelId = incoming.channelId,
                title = incoming.title,
                text = incoming.text,
            )
        )

        val unsure = classification.decision == Decision.UNSURE
        val direction = if (unsure) classification.lean else classification.decision

        db.verdicts().insertIfAbsent(
            TemplateVerdict(
                fingerprint = fingerprint,
                packageName = incoming.packageName,
                verdict = if (direction == Decision.HIDE) Verdict.HIDE else Verdict.KEEP,
                reason = classification.reason,
                source = VerdictSource.MODEL,
                needsVerdict = unsure,
                score = classification.score,
                channelId = incoming.channelId.orEmpty(),
                sampleTitle = incoming.title,
                sampleText = incoming.text,
            )
        )

        if (direction == Decision.HIDE) {
            autoBlockChannelIfRepeated(incoming.packageName, incoming.channelId)
        }

        // Re-read rather than trusting what was just written: the user may have
        // answered this template from the queue while classification was in flight,
        // and a user answer always outranks a model proposal.
        val effective = db.verdicts().find(fingerprint) ?: return
        log(rule, incoming, effective, fromCache = false)
        apply(effective, rule, incoming, sbn, host)

        if (effective.needsVerdict) notifyQueue(host)
    }

    internal suspend fun autoBlockChannelIfRepeated(packageName: String, channelId: String?) {
        if (channelId.isNullOrBlank()) return
        val count = db.verdicts().countHidesForChannel(packageName, channelId)
        if (count >= 2) {
            val existing = db.notificationRules().getActiveRulesForApp(packageName)
                .any {
                    it.matchTarget == MatchTarget.CHANNEL &&
                        it.pattern.equals(channelId, ignoreCase = true) &&
                        it.verdict == Verdict.HIDE
                }
            if (!existing) {
                db.notificationRules().upsert(
                    NotificationRule(
                        packageName = packageName,
                        pattern = channelId,
                        isRegex = false,
                        matchTarget = MatchTarget.CHANNEL,
                        verdict = Verdict.HIDE,
                        enabled = true,
                        description = "Auto-blocked channel after 2 spam verdicts",
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    /**
     * The whole decision table, and it is deliberately short.
     *
     * KEEP re-posts loudly whether it was certain or only leaning, because a
     * maybe-important message still has to reach a user whose source app they have
     * set to Silent. HIDE cancels when it was certain, or when aggressive spam filtering
     * is enabled and the score cleared the aggressive threshold (2.0).
     */
    private fun apply(
        row: TemplateVerdict,
        rule: AppRule,
        incoming: Incoming,
        sbn: StatusBarNotification?,
        host: FilterHost,
    ) {
        if (rule.mode == FilterMode.OBSERVE) return

        val aggressiveSpam = (settings?.aggressiveSpamFiltering ?: true) &&
            row.verdict == Verdict.HIDE &&
            row.score >= AppSettings.AGGRESSIVE_HIDE_THRESHOLD

        when {
            row.verdict == Verdict.KEEP -> {
                // Keeping means "leave it alone" unless the user asked us to supply
                // the sound for an app whose own channels they have silenced. Without
                // sbn there is nothing to copy, and cancelling then would delete a
                // notification and put nothing in its place.
                // Ongoing notifications (foreground service, live UPI/payment) are never cancelled or reposted.
                if (incoming.isOngoing || !rule.repostKeepers || incoming.richContent || sbn == null) return
                // Order matters: post ours first, then drop the original. If the
                // re-post throws, the original is still there and nothing is lost.
                host.repost(sbn, rule.appLabel, incoming.title, incoming.text)
                host.cancel(incoming.key)
            }

            !row.needsVerdict || aggressiveSpam -> host.cancel(incoming.key)

            else -> Unit // unsure, leaning hide: untouched, silent, full fidelity
        }
    }

    private suspend fun notifyQueue(host: FilterHost) {
        val pending = db.verdicts().queueSize()
        if (pending == 0) {
            host.clearNudge()
            return
        }
        val labels = db.verdicts().queuedPackages().mapNotNull { pkg ->
            db.appRules().enabledRule(pkg)?.appLabel
        }
        host.nudge(pending, labels)
    }

    private fun isAmazonPayNotification(incoming: Incoming): Boolean {
        val channel = incoming.channelId.orEmpty().lowercase()
        val content = "${incoming.title} ${incoming.text}".lowercase()
        return channel.contains("pay") || channel.contains("upi") || channel.contains("financial") ||
            channel.contains("wallet") || content.contains("amazon pay") || content.contains("apay") ||
            content.contains("upi") || content.contains("cashback") || content.contains("paid ₹") ||
            content.contains("paid rs") || content.contains("credited") || content.contains("debited")
    }

    private suspend fun log(
        rule: AppRule,
        incoming: Incoming,
        row: TemplateVerdict,
        fromCache: Boolean,
    ) {
        val aggressiveSpam = (settings?.aggressiveSpamFiltering ?: true) &&
            row.verdict == Verdict.HIDE &&
            row.score >= AppSettings.AGGRESSIVE_HIDE_THRESHOLD

        val action = when {
            rule.mode == FilterMode.OBSERVE -> LogAction.OBSERVED
            row.verdict == Verdict.KEEP -> LogAction.KEPT
            !row.needsVerdict || aggressiveSpam -> LogAction.HIDDEN
            else -> LogAction.ASKED
        }

        val effectiveAppLabel = if (rule.packageName.contains("amazon", ignoreCase = true) &&
            isAmazonPayNotification(incoming)) {
            "Amazon Pay"
        } else {
            rule.appLabel
        }

        db.log().insert(
            LogEntry(
                packageName = incoming.packageName,
                appLabel = effectiveAppLabel,
                title = incoming.title,
                text = incoming.text,
                fingerprint = row.fingerprint,
                action = action,
                reason = row.reason,
                source = row.source,
                fromCache = fromCache,
            )
        )
    }

    /**
     * Whether a flat title/text copy would lose something the user needs.
     *
     * Checked against the template name the platform records rather than the style
     * object, because the style itself is not readable back off a posted
     * notification.
     */
    private fun isRich(notification: Notification, extras: android.os.Bundle): Boolean {
        if (notification.actions?.isNotEmpty() == true) return true
        if (extras.containsKey(Notification.EXTRA_PICTURE)) return true
        if (extras.containsKey(Notification.EXTRA_MESSAGES)) return true
        if (extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0) return true
        if (extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)) return true

        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        return RICH_TEMPLATES.any { template.endsWith(it) }
    }

    fun extract(sbn: StatusBarNotification): Incoming? {
        val notification = sbn.notification ?: return null
        val flags = notification.flags

        val isOngoing = (flags and Notification.FLAG_ONGOING_EVENT != 0)
        val isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY != 0)

        val extras = notification.extras ?: android.os.Bundle.EMPTY
        var title = (
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)
                ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                ?: extras.getCharSequence("android.title")
            )?.toString().orEmpty().trim()

        val textParts = mutableListOf<String>()
        val mainText = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence("android.text")
            )?.toString().orEmpty().trim()
        if (mainText.isNotBlank()) textParts.add(mainText)

        val subText = (extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
            ?: extras.getCharSequence("android.subText"))?.toString().orEmpty().trim()
        if (subText.isNotBlank() && !textParts.contains(subText)) textParts.add(subText)

        val summaryText = (extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)
            ?: extras.getCharSequence("android.summaryText"))?.toString().orEmpty().trim()
        if (summaryText.isNotBlank() && !textParts.contains(summaryText)) textParts.add(summaryText)

        val infoText = (extras.getCharSequence(Notification.EXTRA_INFO_TEXT)
            ?: extras.getCharSequence("android.infoText"))?.toString().orEmpty().trim()
        if (infoText.isNotBlank() && !textParts.contains(infoText)) textParts.add(infoText)

        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { line ->
            val l = line?.toString().orEmpty().trim()
            if (l.isNotBlank() && !textParts.contains(l)) textParts.add(l)
        }

        // MessagingStyle messages (e.g. EXTRA_MESSAGES)
        val messages = runCatching {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        }.getOrNull()
        if (messages != null) {
            for (msg in messages) {
                if (msg is android.os.Bundle) {
                    val mText = msg.getCharSequence("text")?.toString().orEmpty().trim()
                    if (mText.isNotBlank() && !textParts.contains(mText)) {
                        textParts.add(mText)
                    }
                }
            }
        }

        if (textParts.isEmpty()) {
            val ticker = notification.tickerText?.toString().orEmpty().trim()
            if (ticker.isNotBlank()) textParts.add(ticker)
        }

        // Fallback search through all extras for any other CharSequence if title/text are still empty
        if (title.isBlank() || textParts.isEmpty()) {
            for (key in extras.keySet()) {
                if (key.startsWith("android.support") || key.startsWith("androidx.")) continue
                val value = runCatching { extras.get(key) }.getOrNull()
                if (value is CharSequence && value.isNotBlank()) {
                    val str = value.toString().trim()
                    if (title.isBlank() && key.contains("title", ignoreCase = true)) {
                        title = str
                    } else if (!textParts.contains(str) && str.length > 2) {
                        textParts.add(str)
                    }
                }
            }
        }

        // If title is still blank, fallback to channelId or tickerText or app name
        if (title.isBlank()) {
            val channel = notification.channelId.orEmpty()
            title = when {
                channel.isNotBlank() && channel != "miscellaneous" ->
                    channel.replace('_', ' ').replace('-', ' ').trim()
                        .split(" ")
                        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                !notification.tickerText.isNullOrBlank() -> notification.tickerText.toString().trim()
                sbn.packageName.contains("amazon", ignoreCase = true) -> "Amazon Pay"
                else -> ""
            }
        }

        var text = textParts.joinToString("\n")
        if (text.isBlank() && title.isNotBlank()) {
            text = notification.channelId.orEmpty()
        }

        // Pure empty group summary with zero content: skip it to avoid disrupting group shade
        if (isGroupSummary && title.isBlank() && text.isBlank()) {
            return null
        }

        // If still completely blank, fallback to placeholder rather than dropping
        if (title.isBlank() && text.isBlank()) {
            if (isGroupSummary) return null
            title = if (sbn.packageName.contains("amazon", ignoreCase = true)) "Amazon Notification" else "Notification"
            text = notification.channelId.takeUnless { it.isNullOrBlank() } ?: "[Content]"
        }

        return Incoming(
            packageName = sbn.packageName,
            key = sbn.key,
            channelId = notification.channelId,
            title = title,
            text = text,
            richContent = isRich(notification, extras),
            isOngoing = isOngoing,
        )
    }

    companion object {
        internal fun findFuzzyMatch(
            incomingFeatures: Set<String>,
            candidates: List<TemplateVerdict>,
            threshold: Double = 0.60,
        ): TemplateVerdict? {
            if (incomingFeatures.isEmpty() || candidates.isEmpty()) return null
            var bestCandidate: TemplateVerdict? = null
            var highestSimilarity = 0.0

            for (candidate in candidates) {
                val candidateFeatures = Tokenizer.features(
                    candidate.channelId.takeIf { it.isNotBlank() },
                    candidate.sampleTitle,
                    candidate.sampleText,
                )
                val intersection = incomingFeatures.intersect(candidateFeatures).size
                val union = incomingFeatures.union(candidateFeatures).size
                if (union > 0) {
                    val similarity = intersection.toDouble() / union.toDouble()
                    if (similarity >= threshold && similarity > highestSimilarity) {
                        highestSimilarity = similarity
                        bestCandidate = candidate
                    }
                }
            }
            return bestCandidate
        }

        /** Platform style classes a plain title/text re-post cannot reproduce. */
        val RICH_TEMPLATES = listOf(
            "MessagingStyle",
            "BigPictureStyle",
            "MediaStyle",
            "DecoratedMediaCustomViewStyle",
            "InboxStyle",
            "CallStyle",
        )
    }
}
