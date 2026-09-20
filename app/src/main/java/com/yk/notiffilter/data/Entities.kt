package com.yk.notiffilter.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** What the filter decided to do with a notification. */
enum class Verdict { KEEP, HIDE }

/** Where a verdict came from. A user override always outranks the model. */
enum class VerdictSource { MODEL, USER }

/**
 * An app the user has opted in. Apps without a row here are never inspected,
 * never classified, and never sent anywhere.
 */
@Entity(tableName = "app_rules")
data class AppRule(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    /**
     * Free-text instruction handed to the model verbatim, e.g.
     * "Keep order status, delivery updates and refunds. Drop coupons and restaurant ads."
     */
    val prose: String,
    val enabled: Boolean = true,
    /**
     * Re-post kept notifications on our own high-importance channel so they make a
     * sound even when the source app's channels have been set to Silent.
     */
    val repostKeepers: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * The verdict cache, keyed by template fingerprint.
 *
 * This is really a user-editable rule table that the model merely bootstraps: a row
 * written by the model can be flipped by the user from the log, after which it is
 * pinned ([source] = USER) and never re-classified.
 */
@Entity(
    tableName = "template_verdicts",
    indices = [Index("packageName")],
)
data class TemplateVerdict(
    @PrimaryKey val fingerprint: String,
    val packageName: String,
    val verdict: Verdict,
    val reason: String,
    val source: VerdictSource,
    /** A representative example, purely so the rule is recognisable in the UI. */
    val sampleTitle: String,
    val sampleText: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** What happened to a notification. Only opted-in apps are ever logged. */
enum class LogAction {
    /** Classification is in flight; the notification is on screen (optimistic show). */
    PENDING,
    KEPT,
    HIDDEN,
    /** Classification failed, so the notification was left alone. */
    ERROR,
}

@Entity(
    tableName = "notification_log",
    indices = [Index("postedAt"), Index("fingerprint")],
)
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val fingerprint: String,
    val action: LogAction,
    val reason: String,
    val source: VerdictSource?,
    /** True when the verdict came from cache rather than a fresh model call. */
    val fromCache: Boolean,
    val postedAt: Long = System.currentTimeMillis(),
)
