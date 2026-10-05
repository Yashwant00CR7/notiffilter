package com.yk.notiffilter.service

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.yk.notiffilter.classify.ClassifyRequest
import com.yk.notiffilter.classify.Decision
import com.yk.notiffilter.classify.NotificationClassifier
import com.yk.notiffilter.data.AppRule
import com.yk.notiffilter.data.FilterMode
import com.yk.notiffilter.data.LogAction
import com.yk.notiffilter.data.LogEntry
import com.yk.notiffilter.data.NotifFilterDatabase
import com.yk.notiffilter.data.TemplateVerdict
import com.yk.notiffilter.data.Verdict
import com.yk.notiffilter.data.VerdictSource
import com.yk.notiffilter.fingerprint.Fingerprinter

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
) {

    /** Extracted so the decision can be unit-tested without a StatusBarNotification. */
    data class Incoming(
        val packageName: String,
        val key: String,
        val channelId: String?,
        val title: String,
        val text: String,
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

        // Tier 1: Pinned template from database (user overrides or previously settled verdicts)
        val known = db.verdicts().find(fingerprint)
        if (known != null) {
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
                source = VerdictSource.USER,
                needsVerdict = false,
                score = if (ruleMatch.verdict == Verdict.HIDE) 10.0 else -10.0,
                channelId = incoming.channelId.orEmpty(),
                sampleTitle = incoming.title,
                sampleText = incoming.text,
            )
            db.verdicts().insertIfAbsent(templateVerdict)
            val effective = db.verdicts().find(fingerprint) ?: templateVerdict
            log(rule, incoming, effective, fromCache = false)
            apply(effective, rule, incoming, sbn, host)
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

        // Re-read rather than trusting what was just written: the user may have
        // answered this template from the queue while classification was in flight,
        // and a user answer always outranks a model proposal.
        val effective = db.verdicts().find(fingerprint) ?: return
        log(rule, incoming, effective, fromCache = false)
        apply(effective, rule, incoming, sbn, host)

        if (effective.needsVerdict) notifyQueue(host)
    }

    /**
     * The whole decision table, and it is deliberately short.
     *
     * KEEP re-posts loudly whether it was certain or only leaning, because a
     * maybe-important message still has to reach a user whose source app they have
     * set to Silent. HIDE only cancels when it was certain; an unsure lean towards
     * hide leaves the notification exactly as the source app posted it — silent,
     * intact, and waiting in the shade rather than destroyed on a guess.
     */
    private fun apply(
        row: TemplateVerdict,
        rule: AppRule,
        incoming: Incoming,
        sbn: StatusBarNotification?,
        host: FilterHost,
    ) {
        if (rule.mode == FilterMode.OBSERVE) return

        when {
            row.verdict == Verdict.KEEP -> {
                if (!rule.repostKeepers) return
                // Order matters: post ours first, then drop the original. If the
                // re-post throws, the original is still there and nothing is lost.
                if (sbn != null) {
                    host.repost(sbn, rule.appLabel, incoming.title, incoming.text)
                }
                host.cancel(incoming.key)
            }

            !row.needsVerdict -> host.cancel(incoming.key)

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

    private suspend fun log(
        rule: AppRule,
        incoming: Incoming,
        row: TemplateVerdict,
        fromCache: Boolean,
    ) {
        val action = when {
            rule.mode == FilterMode.OBSERVE -> LogAction.OBSERVED
            row.needsVerdict -> LogAction.ASKED
            row.verdict == Verdict.KEEP -> LogAction.KEPT
            else -> LogAction.HIDDEN
        }
        db.log().insert(
            LogEntry(
                packageName = incoming.packageName,
                appLabel = rule.appLabel,
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

    private fun extract(sbn: StatusBarNotification): Incoming? {
        val notification = sbn.notification ?: return null
        val flags = notification.flags

        // Group summaries only mirror their children, and cancelling one takes the
        // whole group with it.
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        // Ongoing notifications are foreground-service ones — live order tracking,
        // music, calls. They cannot be cancelled and should not be.
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) return null

        val extras = notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString().orEmpty()

        if (title.isBlank() && text.isBlank()) return null

        return Incoming(
            packageName = sbn.packageName,
            key = sbn.key,
            channelId = notification.channelId,
            title = title,
            text = text,
        )
    }
}
