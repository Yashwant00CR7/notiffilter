package com.yk.notiffilter.classify

/** One of the user's own corrections, replayed to the model as a labelled example. */
data class Example(val title: String, val text: String, val keep: Boolean)

data class ClassifyRequest(
    val appLabel: String,
    /** The user's free-text instruction for this app, handed to the model verbatim. */
    val prose: String,
    val title: String,
    val text: String,
    val examples: List<Example>,
)

data class Classification(val keep: Boolean, val reason: String)

/**
 * The seam that keeps provider choice from being a one-way door — the same
 * dependency-injection shape as agentcache's embedding providers. Nothing outside
 * this package knows which service is answering.
 */
interface NotificationClassifier {
    suspend fun classify(request: ClassifyRequest): Result<Classification>

    /** Cheap round-trip so Settings can tell "bad key" from "bad prose". */
    suspend fun testConnection(): Result<String>
}
