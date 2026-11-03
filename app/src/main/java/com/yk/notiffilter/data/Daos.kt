package com.yk.notiffilter.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules ORDER BY appLabel COLLATE NOCASE")
    fun observeAll(): Flow<List<AppRule>>

    @Query("SELECT * FROM app_rules")
    suspend fun getAll(): List<AppRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<AppRule>)

    @Query("SELECT * FROM app_rules WHERE packageName = :pkg")
    fun observe(pkg: String): Flow<AppRule?>

    @Query("SELECT * FROM app_rules WHERE packageName = :pkg AND enabled = 1")
    suspend fun enabledRule(pkg: String): AppRule?

    @Upsert
    suspend fun upsert(rule: AppRule)

    @Query("UPDATE app_rules SET mode = :mode WHERE packageName = :pkg")
    suspend fun setMode(pkg: String, mode: FilterMode)

    @Query("DELETE FROM app_rules WHERE packageName = :pkg")
    suspend fun delete(pkg: String)
}

@Dao
interface TemplateVerdictDao {
    @Query("SELECT * FROM template_verdicts WHERE fingerprint = :fingerprint")
    suspend fun find(fingerprint: String): TemplateVerdict?

    @Upsert
    suspend fun upsert(verdict: TemplateVerdict)

    @Query("SELECT * FROM template_verdicts")
    suspend fun getAll(): List<TemplateVerdict>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(verdicts: List<TemplateVerdict>)

    /**
     * Only writes a model proposal when nothing is stored yet, so a classification
     * can never clobber a user answer that landed first.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(verdict: TemplateVerdict): Long

    /**
     * The training set: templates the user personally ruled on. Model-authored rows
     * are excluded on purpose — they are cache entries, not labels.
     */
    @Query("SELECT * FROM template_verdicts WHERE packageName = :pkg AND source = 'USER' AND needsVerdict = 0")
    suspend fun trainingForApp(pkg: String): List<TemplateVerdict>

    @Query("SELECT * FROM template_verdicts WHERE source = 'USER' AND needsVerdict = 0")
    suspend fun trainingGlobal(): List<TemplateVerdict>

    /** The ask queue, oldest first so the backlog is cleared in the order it arrived. */
    @Query("SELECT * FROM template_verdicts WHERE needsVerdict = 1 ORDER BY updatedAt ASC")
    fun observeQueue(): Flow<List<TemplateVerdict>>

    @Query("SELECT COUNT(*) FROM template_verdicts WHERE needsVerdict = 1")
    suspend fun queueSize(): Int

    @Query("SELECT COUNT(*) FROM template_verdicts WHERE packageName = :pkg AND channelId = :channelId AND verdict = 'HIDE'")
    suspend fun countHidesForChannel(pkg: String, channelId: String): Int

    @Query("SELECT DISTINCT packageName FROM template_verdicts WHERE needsVerdict = 1")
    suspend fun queuedPackages(): List<String>

    /** Settles a queued proposal. This, and only this, creates training data. */
    @Query(
        """
        UPDATE template_verdicts
        SET verdict = :verdict, source = 'USER', needsVerdict = 0,
            reason = :reason, updatedAt = :now
        WHERE fingerprint = :fingerprint
        """
    )
    suspend fun answer(fingerprint: String, verdict: Verdict, reason: String, now: Long)

    @Query("SELECT * FROM template_verdicts WHERE packageName = :pkg ORDER BY updatedAt DESC")
    fun observeForApp(pkg: String): Flow<List<TemplateVerdict>>

    @Query("DELETE FROM template_verdicts WHERE packageName = :pkg")
    suspend fun clearForApp(pkg: String)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM notification_log ORDER BY postedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<LogEntry>>

    @Query("SELECT * FROM notification_log WHERE id = :id")
    suspend fun find(id: Long): LogEntry?

    @Insert
    suspend fun insert(entry: LogEntry): Long

    /** Keeps every entry sharing a fingerprint in step after a user answer. */
    @Query(
        """
        UPDATE notification_log
        SET action = :action, reason = :reason, source = 'USER'
        WHERE fingerprint = :fingerprint
        """
    )
    suspend fun applyOverrideToHistory(fingerprint: String, action: LogAction, reason: String)

    @Query("DELETE FROM notification_log WHERE postedAt < :cutoff")
    suspend fun pruneBefore(cutoff: Long)

    @Query("DELETE FROM notification_log")
    suspend fun clear()
}

@Dao
interface NotificationRuleDao {
    @Query("SELECT * FROM notification_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NotificationRule>>

    @Query("SELECT * FROM notification_rules WHERE packageName IS NULL ORDER BY createdAt DESC")
    fun observeGlobal(): Flow<List<NotificationRule>>

    @Query("SELECT * FROM notification_rules WHERE packageName = :pkg ORDER BY createdAt DESC")
    fun observeForApp(pkg: String): Flow<List<NotificationRule>>

    @Query("SELECT * FROM notification_rules WHERE enabled = 1 AND (packageName IS NULL OR packageName = :pkg)")
    suspend fun getActiveRulesForApp(pkg: String): List<NotificationRule>

    @Query("SELECT * FROM notification_rules WHERE enabled = 1")
    suspend fun getAllActiveRules(): List<NotificationRule>

    @Query("SELECT * FROM notification_rules WHERE enabled = 1")
    fun observeAllActive(): Flow<List<NotificationRule>>

    @Query("SELECT COUNT(*) FROM notification_rules")
    suspend fun count(): Int

    @Query("SELECT * FROM notification_rules")
    suspend fun getAll(): List<NotificationRule>

    @Query("SELECT * FROM notification_rules WHERE id = :id")
    suspend fun find(id: Long): NotificationRule?

    @Upsert
    suspend fun upsert(rule: NotificationRule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<NotificationRule>)

    @Query("DELETE FROM notification_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM notification_rules WHERE packageName = :pkg")
    suspend fun deleteForApp(pkg: String)

    @Query("DELETE FROM notification_rules")
    suspend fun clearAll()
}
