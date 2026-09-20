package com.yk.notiffilter.service

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.yk.notiffilter.classify.ClassifyRequest
import com.yk.notiffilter.classify.Example
import com.yk.notiffilter.classify.NotificationClassifier
import com.yk.notiffilter.data.AppRule
import com.yk.notiffilter.data.LogAction
import com.yk.notiffilter.data.LogEntry
import com.yk.notiffilter.data.NotifFilterDatabase
import com.yk.notiffilter.data.TemplateVerdict
import com.yk.notiffilter.data.Verdict
import com.yk.notiffilter.data.VerdictSource
import com.yk.notiffilter.fingerprint.Fingerprinter
import com.yk.notiffilter.settings.SecureSettings

/** What the engine needs from the platform. Keeps the decision logic off the service. */
interface FilterHost {
    fun cancel(key: String)
    fun repost(sbn: StatusBarNotification, appLabel: String, title: String, text: String)
}

/**
 * The live path.
 *
 * The governing rule is optimistic show: a notification stays on screen until
 * something positively decides to hide it. Every failure mode — offline, rate
 * limited, API down, process killed mid-flight — therefore degrades to "the user
 * sees the notification", which is exactly the behaviour without this app installed.
 * Nothing is ever silently lost.
 */
class FilterEngine(
    private val db: NotifFilterDatabase,
    private val settings: SecureSettings,
    private val classifierFor: () -> NotificationClassifier?,
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
        val rule = db.appRules().enabledRule(incoming.packageName) ?: return

        val fingerprint = Fingerprinter.fingerprint(
            incoming.packageName,
            incoming.channelId,
            incoming.title,
            incoming.text,
        )

        db.verdicts().find(fingerprint)?.let { cached ->
            log(rule, incoming, fingerprint, cached.verdict, cached.reason, cached.source, true)
            applyVerdict(cached.verdict, sbn, rule, incoming, host)
            return
        }

        // Cache miss. Log the notification as pending and leave it on screen while
        // the model is consulted.
        val logId = db.log().insert(
            entry(rule, incoming, fingerprint, LogAction.PENDING, "Classifying…", null, false)
        )

        val classifier = classifierFor()
        if (classifier == null) {
            db.log().resolve(
                logId, LogAction.ERROR, "No API key configured", VerdictSource.MODEL, false,
            )
            return
        }

        val examples = db.verdicts()
            .corrections(incoming.packageName, settings.exampleLimit)
            .map { Example(it.sampleTitle, it.sampleText, it.verdict == Verdict.KEEP) }

        val result = classifier.classify(
            ClassifyRequest(
                appLabel = rule.appLabel,
                prose = rule.prose,
                title = incoming.title,
                text = incoming.text,
                examples = examples,
            )
        )

        result.onFailure { error ->
            db.log().resolve(
                logId,
                LogAction.ERROR,
                error.message?.take(200) ?: "Classification failed",
                VerdictSource.MODEL,
                false,
            )
        }

        val classification = result.getOrNull() ?: return
        val verdict = if (classification.keep) Verdict.KEEP else Verdict.HIDE

        db.verdicts().insertIfAbsent(
            TemplateVerdict(
                fingerprint = fingerprint,
                packageName = incoming.packageName,
                verdict = verdict,
                reason = classification.reason,
                source = VerdictSource.MODEL,
                sampleTitle = incoming.title,
                sampleText = incoming.text,
            )
        )

        // Re-read rather than trusting what we just wrote: the user may have pinned an
        // override for this fingerprint from the log while the call was in flight, and
        // a user override always outranks the model.
        val effective = db.verdicts().find(fingerprint)
        val finalVerdict = effective?.verdict ?: verdict
        val reason = effective?.reason ?: classification.reason
        val source = effective?.source ?: VerdictSource.MODEL

        db.log().resolve(logId, finalVerdict.toAction(), reason, source, false)
        applyVerdict(finalVerdict, sbn, rule, incoming, host)
    }

    private fun applyVerdict(
        verdict: Verdict,
        sbn: StatusBarNotification,
        rule: AppRule,
        incoming: Incoming,
        host: FilterHost,
    ) {
        when (verdict) {
            Verdict.HIDE -> host.cancel(incoming.key)
            Verdict.KEEP -> {
                if (!rule.repostKeepers) return
                // Order matters: post ours first, then drop the original. If the
                // re-post throws, the original is still there and nothing is lost.
                host.repost(sbn, rule.appLabel, incoming.title, incoming.text)
                host.cancel(incoming.key)
            }
        }
    }

    private suspend fun log(
        rule: AppRule,
        incoming: Incoming,
        fingerprint: String,
        verdict: Verdict,
        reason: String,
        source: VerdictSource,
        fromCache: Boolean,
    ) {
        db.log().insert(
            entry(rule, incoming, fingerprint, verdict.toAction(), reason, source, fromCache)
        )
    }

    private fun entry(
        rule: AppRule,
        incoming: Incoming,
        fingerprint: String,
        action: LogAction,
        reason: String,
        source: VerdictSource?,
        fromCache: Boolean,
    ) = LogEntry(
        packageName = incoming.packageName,
        appLabel = rule.appLabel,
        title = incoming.title,
        text = incoming.text,
        fingerprint = fingerprint,
        action = action,
        reason = reason,
        source = source,
        fromCache = fromCache,
    )

    private fun Verdict.toAction() = when (this) {
        Verdict.KEEP -> LogAction.KEPT
        Verdict.HIDE -> LogAction.HIDDEN
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
