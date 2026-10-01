package com.yk.notiffilter

import com.yk.notiffilter.data.AppRule
import com.yk.notiffilter.data.FilterMode
import com.yk.notiffilter.data.LogAction
import com.yk.notiffilter.data.LogEntry
import com.yk.notiffilter.data.NotifFilterDatabase
import com.yk.notiffilter.data.TemplateVerdict
import com.yk.notiffilter.data.Verdict
import com.yk.notiffilter.data.VerdictSource
import com.yk.notiffilter.notify.KeeperReposter
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
        refreshNudge()
    }

    /**
     * Flips a verdict from the log — the correction path for something the model got
     * wrong on its own. Identical in effect to answering a queued template: the row
     * becomes USER-sourced, leaves the queue, and starts training the model.
     */
    suspend fun override(entry: LogEntry, reason: String = "Corrected by you") {
        val existing = db.verdicts().find(entry.fingerprint)
        val flipped = when (entry.action) {
            LogAction.HIDDEN -> Verdict.KEEP
            LogAction.KEPT -> Verdict.HIDE
            // An ASKED or OBSERVED row was never acted on, so "wrong" means the lean
            // was wrong; flip whatever the model was leaning towards.
            else -> if (existing?.verdict == Verdict.HIDE) Verdict.KEEP else Verdict.HIDE
        }
        db.verdicts().upsert(
            TemplateVerdict(
                fingerprint = entry.fingerprint,
                packageName = entry.packageName,
                verdict = flipped,
                reason = reason,
                source = VerdictSource.USER,
                needsVerdict = false,
                score = existing?.score ?: 0.0,
                channelId = existing?.channelId.orEmpty(),
                sampleTitle = entry.title,
                sampleText = entry.text,
            )
        )
        db.log().applyOverrideToHistory(
            entry.fingerprint,
            if (flipped == Verdict.KEEP) LogAction.KEPT else LogAction.HIDDEN,
            reason,
        )
        refreshNudge()
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
