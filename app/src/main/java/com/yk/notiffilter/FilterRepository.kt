package com.yk.notiffilter

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
import com.yk.notiffilter.notify.KeeperReposter
import com.yk.notiffilter.service.FilterListenerService
import com.yk.notiffilter.settings.AppSettings
import kotlinx.coroutines.flow.Flow

class FilterRepository(
    private val db: NotifFilterDatabase,
    private val settings: AppSettings,
    private val reposter: KeeperReposter? = null,
) {
    fun rules(): Flow<List<AppRule>> = db.appRules().observeAll()
    fun rule(pkg: String): Flow<AppRule?> = db.appRules().observe(pkg)
    fun log(): Flow<List<LogEntry>> = db.log().observeRecent()
    fun verdicts(pkg: String): Flow<List<TemplateVerdict>> = db.verdicts().observeForApp(pkg)

    /** Notification keyword and channel rules. */
    fun notificationRules(): Flow<List<NotificationRule>> = db.notificationRules().observeAll()
    fun globalRules(): Flow<List<NotificationRule>> = db.notificationRules().observeGlobal()
    fun notificationRulesForApp(pkg: String): Flow<List<NotificationRule>> = db.notificationRules().observeForApp(pkg)

    suspend fun saveNotificationRule(rule: NotificationRule): Long = db.notificationRules().upsert(rule)
    suspend fun deleteNotificationRule(id: Long) = db.notificationRules().delete(id)

    /** Templates the model declined to decide alone. This is the app's to-do list. */
    fun queue(): Flow<List<TemplateVerdict>> = db.verdicts().observeQueue()

    suspend fun saveRule(rule: AppRule) = db.appRules().upsert(rule)

    suspend fun setMode(pkg: String, mode: FilterMode) = db.appRules().setMode(pkg, mode)

    suspend fun deleteRule(pkg: String) {
        db.appRules().delete(pkg)
        db.verdicts().clearForApp(pkg)
        refreshNudge()
    }

    /**
     * Settles a queued template. This is the only thing that creates training data.
     *
     * The answer is pinned permanently, so the same question is never asked twice,
     * and it simultaneously becomes evidence that steers every future template the
     * model has not seen — which is what lets the queue shrink instead of growing.
     */
    suspend fun answer(template: TemplateVerdict, keep: Boolean) {
        val verdict = if (keep) Verdict.KEEP else Verdict.HIDE
        db.verdicts().answer(
            fingerprint = template.fingerprint,
            verdict = verdict,
            reason = "You decided",
            now = System.currentTimeMillis(),
        )
        db.log().applyOverrideToHistory(
            template.fingerprint,
            if (keep) LogAction.KEPT else LogAction.HIDDEN,
            "You decided",
        )
        if (!keep) {
            if (template.channelId.isNotBlank()) {
                checkAndAutoBlockChannel(template.packageName, template.channelId)
            }
            FilterListenerService.dismissMatching(
                packageName = template.packageName,
                fingerprint = template.fingerprint,
                title = template.sampleTitle,
                text = template.sampleText,
            )
        }
        refreshNudge()
    }

    /**
     * Sets an explicit KEEP or HIDE verdict from the history/log.
     *
     * Identical in effect to answering a queued template: the row becomes USER-sourced,
     * leaves the queue, and starts training the model. If set to HIDE, any active
     * notifications matching this entry in the shade are immediately cleared.
     */
    suspend fun setVerdict(
        entry: LogEntry,
        keep: Boolean,
        reason: String = "You decided",
    ) {
        val targetVerdict = if (keep) Verdict.KEEP else Verdict.HIDE
        val targetAction = if (keep) LogAction.KEPT else LogAction.HIDDEN
        val existing = db.verdicts().find(entry.fingerprint)

        db.verdicts().upsert(
            TemplateVerdict(
                fingerprint = entry.fingerprint,
                packageName = entry.packageName,
                verdict = targetVerdict,
                reason = reason,
                source = VerdictSource.USER,
                needsVerdict = false,
                score = existing?.score ?: if (keep) -10.0 else 10.0,
                channelId = existing?.channelId.orEmpty(),
                sampleTitle = entry.title,
                sampleText = entry.text,
            )
        )
        db.log().applyOverrideToHistory(
            entry.fingerprint,
            targetAction,
            reason,
        )
        if (!keep) {
            val channel = existing?.channelId.orEmpty()
            if (channel.isNotBlank()) {
                checkAndAutoBlockChannel(entry.packageName, channel)
            }
            FilterListenerService.dismissMatching(
                packageName = entry.packageName,
                fingerprint = entry.fingerprint,
                title = entry.title,
                text = entry.text,
            )
        }
        refreshNudge()
    }

    /**
     * Flips a verdict from the log — the correction path for something the model got
     * wrong on its own.
     */
    suspend fun override(entry: LogEntry, reason: String = "Corrected by you") {
        val existing = db.verdicts().find(entry.fingerprint)
        val keep = when (entry.action) {
            LogAction.HIDDEN -> true
            LogAction.KEPT -> false
            else -> existing?.verdict != Verdict.KEEP
        }
        setVerdict(entry, keep, reason)
    }

    /**
     * Automatically creates an app-scoped channel HIDE rule when 2 or more notifications
     * from the same channel have been confirmed as HIDE (spam).
     */
    suspend fun checkAndAutoBlockChannel(packageName: String, channelId: String) {
        if (channelId.isBlank()) return
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

    /** Keeps the silent nudge honest: it must vanish the moment the queue is empty. */
    private suspend fun refreshNudge() {
        val reposter = reposter ?: return
        val pending = db.verdicts().queueSize()
        if (pending == 0) {
            reposter.clearNudge()
            return
        }
        val labels = db.verdicts().queuedPackages().mapNotNull { pkg ->
            db.appRules().enabledRule(pkg)?.appLabel
        }
        reposter.nudge(pending, labels)
    }

    suspend fun clearLog() = db.log().clear()

    suspend fun pruneLog(days: Int = settings.logRetentionDays) =
        db.log().pruneBefore(System.currentTimeMillis() - days * 86_400_000L)

    fun settings(): AppSettings = settings
}
