package com.yk.notiffilter.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** What the filter decided to do with a notification. */
enum class Verdict { KEEP, HIDE }

/** Where a verdict came from. A user override always outranks the model. */
enum class VerdictSource { MODEL, USER }

/**
 * How far along an app is.
 *
 * A newly added app starts in [OBSERVE]: it is logged and templates are collected,
 * but nothing is hidden, nothing is re-posted, and the app is left entirely loud.
 * Observing is free of risk by construction, which is what makes it an acceptable
 * answer to a classifier that knows nothing on day one.
 */
enum class FilterMode { OBSERVE, ACTIVE }

/**
 * An app the user has opted in. Apps without a row here are never inspected.
 *
 * There is no longer a written instruction: the app is taught by labelling its actual
 * notifications, which is both more precise than prose and the only thing an on-device
 * counting model can learn from.
 */
@Entity(tableName = "app_rules")
data class AppRule(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val mode: FilterMode = FilterMode.OBSERVE,
    val enabled: Boolean = true,
    /**
     * Re-post kept notifications on our own high-importance channel so they make a
     * sound even when the source app's channels have been set to Silent.
     */
    val repostKeepers: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * The verdict table: one row per template, and the single authority on what happens.
 *
 * It is a user-editable rule table that the model only ever proposes entries for. A
 * row with [needsVerdict] set is a proposal awaiting the user's answer; a row with
 * [source] = USER is settled and never reconsidered.
 *
 * Only settled USER rows train the classifier. Rows the model produced are cache
 * entries, not ground truth — training on them would bake unreviewed guesses into
 * every future decision with no way to tell which ones were wrong.
 */
@Entity(
    tableName = "template_verdicts",
    indices = [Index("packageName"), Index("needsVerdict")],
)
data class TemplateVerdict(
    @PrimaryKey val fingerprint: String,
    val packageName: String,
    /** When [needsVerdict] is set this is the model's lean, not a decision. */
    val verdict: Verdict,
    val reason: String,
    val source: VerdictSource,
    /** In the label queue: the model was not confident enough to decide alone. */
    val needsVerdict: Boolean = false,
    /** Raw log-odds at the time of the proposal, positive towards HIDE. */
    val score: Double = 0.0,
    /**
     * Stored because the channel is one of the classifier's features and the
     * fingerprint only carries it in hashed form. Without this column a labelled
     * template could not be re-tokenised into the example that taught the model.
     */
    val channelId: String = "",
    /** A representative example, so the rule is recognisable in the UI. */
    val sampleTitle: String,
    val sampleText: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** What happened to a notification. Only opted-in apps are ever logged. */
enum class LogAction {
    /** Observe mode: recorded and left completely alone. */
    OBSERVED,
    KEPT,
    HIDDEN,
    /** Unsure — queued for the user's verdict. */
    ASKED,

    /**
     * Retained so log rows written before the on-device rewrite still deserialise.
     * Nothing produces these any more: classification no longer does I/O, so it can
     * neither be pending nor fail.
     */
    PENDING,
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
    /** True when the verdict came from the table rather than a fresh classification. */
    val fromCache: Boolean,
    val postedAt: Long = System.currentTimeMillis(),
)

/** Where in the notification to search for a pattern match. */
enum class MatchTarget {
    /** Searches both title and text. */
    ANY,
    TITLE,
    TEXT,
    CHANNEL,
}

/**
 * An explicit, deterministic rule.
 *
 * Runs before the Bayesian model. A Whitelist (KEEP) rule ensures critical transactional
 * notifications (OTPs, delivery tracking, banking alerts) can never be hidden. A Blacklist
 * (HIDE) rule silences marketing spam that rewords around statistical models.
 *
 * If [packageName] is null, this rule applies globally to all opted-in apps.
 * If [packageName] is set, this rule only applies to notifications from that specific package.
 */
@Entity(
    tableName = "notification_rules",
    indices = [Index("packageName"), Index("verdict"), Index("enabled")],
)
data class NotificationRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Null for global rules; specific package for app-scoped rules. */
    val packageName: String? = null,
    /** Keyword/phrase or regex pattern. */
    val pattern: String,
    val isRegex: Boolean = false,
    val matchTarget: MatchTarget = MatchTarget.ANY,
    val verdict: Verdict = Verdict.HIDE,
    val enabled: Boolean = true,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
