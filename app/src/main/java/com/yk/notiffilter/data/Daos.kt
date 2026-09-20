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

    @Query("SELECT * FROM app_rules WHERE packageName = :pkg")
    fun observe(pkg: String): Flow<AppRule?>

    @Query("SELECT * FROM app_rules WHERE packageName = :pkg AND enabled = 1")
    suspend fun enabledRule(pkg: String): AppRule?

    @Upsert
    suspend fun upsert(rule: AppRule)

    @Query("DELETE FROM app_rules WHERE packageName = :pkg")
    suspend fun delete(pkg: String)
}

@Dao
interface TemplateVerdictDao {
    @Query("SELECT * FROM template_verdicts WHERE fingerprint = :fingerprint")
    suspend fun find(fingerprint: String): TemplateVerdict?

    @Upsert
    suspend fun upsert(verdict: TemplateVerdict)

    /**
     * Only writes a model verdict when nothing is stored yet, so an in-flight
     * classification can never clobber a user override that landed first.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(verdict: TemplateVerdict): Long

    /**
     * The few-shot block: the user's own corrections, most recent first. These are
     * what let the model generalise to templates it has never seen.
     */
    @Query(
        """
        SELECT * FROM template_verdicts
        WHERE packageName = :pkg AND source = 'USER'
        ORDER BY updatedAt DESC LIMIT :limit
        """
    )
    suspend fun corrections(pkg: String, limit: Int): List<TemplateVerdict>

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

    @Query(
        """
        UPDATE notification_log
        SET action = :action, reason = :reason, source = :source, fromCache = :fromCache
        WHERE id = :id
        """
    )
    suspend fun resolve(
        id: Long,
        action: LogAction,
        reason: String,
        source: VerdictSource,
        fromCache: Boolean,
    )

    /** Keeps every entry sharing a fingerprint in step after a user override. */
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
