package com.yk.notiffilter

import com.yk.notiffilter.data.AppRule
import com.yk.notiffilter.data.LogAction
import com.yk.notiffilter.data.LogEntry
import com.yk.notiffilter.data.NotifFilterDatabase
import com.yk.notiffilter.data.TemplateVerdict
import com.yk.notiffilter.data.Verdict
import com.yk.notiffilter.data.VerdictSource
import com.yk.notiffilter.settings.SecureSettings
import kotlinx.coroutines.flow.Flow

class FilterRepository(
    private val db: NotifFilterDatabase,
    private val settings: SecureSettings,
) {
    fun rules(): Flow<List<AppRule>> = db.appRules().observeAll()
    fun rule(pkg: String): Flow<AppRule?> = db.appRules().observe(pkg)
    fun log(): Flow<List<LogEntry>> = db.log().observeRecent()
    fun verdicts(pkg: String): Flow<List<TemplateVerdict>> = db.verdicts().observeForApp(pkg)

    suspend fun saveRule(rule: AppRule) = db.appRules().upsert(rule)

    suspend fun deleteRule(pkg: String) {
        db.appRules().delete(pkg)
        db.verdicts().clearForApp(pkg)
    }

    /**
     * Pins the opposite verdict for this notification's whole template.
     *
     * This is the correction loop, and it involves no API call: the verdict cache is
     * really a user-editable rule table that the model only bootstraps. Once a
     * fingerprint is marked USER it is never re-classified, so the same mistake
     * cannot recur — and the row simultaneously becomes a few-shot example that
     * steers the model on templates it has not seen yet.
     */
    suspend fun override(entry: LogEntry, reason: String = "Corrected by you") {
        val flipped = when (entry.action) {
            LogAction.HIDDEN -> Verdict.KEEP
            else -> Verdict.HIDE
        }
        db.verdicts().upsert(
            TemplateVerdict(
                fingerprint = entry.fingerprint,
                packageName = entry.packageName,
                verdict = flipped,
                reason = reason,
                source = VerdictSource.USER,
                sampleTitle = entry.title,
                sampleText = entry.text,
            )
        )
        db.log().applyOverrideToHistory(
            entry.fingerprint,
            if (flipped == Verdict.KEEP) LogAction.KEPT else LogAction.HIDDEN,
            reason,
        )
    }

    suspend fun clearLog() = db.log().clear()

    suspend fun pruneLog(days: Int = 30) =
        db.log().pruneBefore(System.currentTimeMillis() - days * 86_400_000L)

    fun settings(): SecureSettings = settings
}
