package com.yk.notiffilter.classify

/** What the classifier was asked about. Everything here stays on the device. */
data class ClassifyRequest(
    val packageName: String,
    val channelId: String?,
    val title: String,
    val text: String,
)

/**
 * The three outcomes. [UNSURE] is not a failure — it is the normal answer for a
 * wording the user has not taught yet, and it routes to the label queue rather than
 * to a guess.
 */
enum class Decision { KEEP, HIDE, UNSURE }

/**
 * @param lean which way the weak evidence points. Only meaningful when [decision] is
 *   [Decision.UNSURE], where it decides whether the notification is re-posted loudly
 *   (lean keep — a maybe-important message must still reach you) or left silent and
 *   untouched (lean hide).
 * @param score the raw log-odds, positive towards HIDE. Surfaced in the log so
 *   "why did it do that" always has an answer.
 */
data class Classification(
    val decision: Decision,
    val lean: Decision,
    val score: Double,
    val reason: String,
)

/** One labelled example: the features of a template the user personally ruled on. */
data class Labelled(val features: Set<String>, val keep: Boolean)

/**
 * Where training data comes from. Only the user's own verdicts train the model, so
 * this is deliberately narrow — see [com.yk.notiffilter.data.VerdictSource].
 */
interface TrainingSource {
    /** Templates in this app that the user has ruled on. */
    suspend fun forApp(packageName: String): List<Labelled>

    /** Every template the user has ruled on, in any app, as the background prior. */
    suspend fun global(): List<Labelled>
}

/**
 * The seam. Kept after the cloud classifier was deleted purely so [FilterEngine]'s
 * decision logic can be unit-tested against a fake — there is no second
 * implementation and, with INTERNET removed from the manifest, there cannot be one
 * that leaves the device.
 */
interface NotificationClassifier {
    suspend fun classify(request: ClassifyRequest): Classification
}
