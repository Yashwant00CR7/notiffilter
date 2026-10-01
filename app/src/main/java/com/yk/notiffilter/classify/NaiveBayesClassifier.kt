package com.yk.notiffilter.classify

import kotlin.math.abs
import kotlin.math.ln

/**
 * A hierarchical naive Bayes classifier trained on the user's own verdicts.
 *
 * Nothing leaves the device and there is no model file: the "model" is a pair of
 * token-count maps recomputed from the verdict table on every cache miss. That costs
 * microseconds on a few dozen rows and buys a guarantee that the model can never
 * drift out of step with the labels the user can see in the UI.
 *
 * Why counting tokens can work where keyword rules cannot: a rule is authored once,
 * static, and decided by a single word. This weighs every feature by evidence and
 * retrains on every correction, so it tracks how one specific app actually writes.
 * Marketing copy varies its words far more than its register — imperatives, prices,
 * urgency, second person — which is why Bayesian spam filters outlived blocklists.
 */
class NaiveBayesClassifier(
    private val training: TrainingSource,
    private val confidenceMargin: Double = DEFAULT_MARGIN,
    private val minExamples: Int = DEFAULT_MIN_EXAMPLES,
) : NotificationClassifier {

    override suspend fun classify(request: ClassifyRequest): Classification {
        val features = Tokenizer.features(request.channelId, request.title, request.text)
        val model = Model.train(training.forApp(request.packageName), training.global())
        return model.classify(features, confidenceMargin, minExamples)
    }

    /**
     * Counts, weighted. App evidence is multiplied by [APP_WEIGHT] on top of the
     * global pool — and since the global pool already contains this app's rows, an
     * app's own labels carry an effective weight of APP_WEIGHT + 1.
     *
     * A brand-new app therefore behaves like the pooled model rather than like a
     * coin flip, and a well-labelled app is governed by its own vocabulary. There is
     * no cold-start cliff between the two.
     */
    class Model private constructor(
        private val hide: Map<String, Double>,
        private val keep: Map<String, Double>,
        private val hideTotal: Double,
        private val keepTotal: Double,
        private val vocabulary: Int,
        private val hideDocs: Int,
        private val keepDocs: Int,
    ) {

        val exampleCount: Int get() = hideDocs + keepDocs

        /** Positive scores point at HIDE. */
        fun score(features: Set<String>): Double {
            if (exampleCount == 0) return 0.0
            var score = ln(prior(hideDocs) / prior(keepDocs))
            features.forEach { score += weight(it) }
            return score
        }

        fun classify(features: Set<String>, margin: Double, minExamples: Int): Classification {
            val score = score(features)
            val lean = if (score > 0) Decision.HIDE else Decision.KEEP

            // Below a handful of examples the log-odds are arithmetically large and
            // epistemically worthless — two labels can produce a confident-looking
            // number. Refusing to act until there is real evidence is what keeps the
            // first week honest.
            if (exampleCount < minExamples) {
                return Classification(
                    decision = Decision.UNSURE,
                    lean = lean,
                    score = score,
                    reason = "Not enough of your labels yet ($exampleCount)",
                )
            }

            val decision = if (abs(score) >= margin) lean else Decision.UNSURE
            return Classification(
                decision = decision,
                lean = lean,
                score = score,
                reason = explain(features, decision, lean, score),
            )
        }

        /**
         * The features that actually moved the verdict, strongest first. A reason the
         * user can check beats a fluent sentence they cannot.
         */
        fun evidence(features: Set<String>, limit: Int = 4): List<Pair<String, Double>> =
            features.map { it to weight(it) }
                .filter { abs(it.second) > 0.05 }
                .sortedByDescending { abs(it.second) }
                .take(limit)

        private fun explain(
            features: Set<String>,
            decision: Decision,
            lean: Decision,
            score: Double,
        ): String {
            val top = evidence(features).joinToString(", ") { readable(it.first) }
            val rounded = String.format("%.1f", score)
            val head = when (decision) {
                Decision.HIDE -> "Hidden"
                Decision.KEEP -> "Kept"
                Decision.UNSURE -> "Unsure, leaning ${if (lean == Decision.HIDE) "hide" else "keep"}"
            }
            return if (top.isBlank()) "$head ($rounded)" else "$head ($rounded): $top"
        }

        private fun weight(feature: String): Double =
            ln(probability(hide, hideTotal, feature) / probability(keep, keepTotal, feature))

        private fun probability(counts: Map<String, Double>, total: Double, feature: String) =
            ((counts[feature] ?: 0.0) + ALPHA) / (total + ALPHA * vocabulary)

        private fun prior(docs: Int) = docs + ALPHA

        companion object {
            private const val ALPHA = 1.0
            private const val APP_WEIGHT = 3.0

            fun train(app: List<Labelled>, global: List<Labelled>): Model {
                val hide = HashMap<String, Double>()
                val keep = HashMap<String, Double>()
                val vocabulary = HashSet<String>()

                fun absorb(examples: List<Labelled>, weight: Double) {
                    examples.forEach { example ->
                        val target = if (example.keep) keep else hide
                        example.features.forEach { feature ->
                            target[feature] = (target[feature] ?: 0.0) + weight
                            vocabulary += feature
                        }
                    }
                }

                absorb(global, 1.0)
                absorb(app, APP_WEIGHT)

                // Document counts are weighted the same way, so the prior reflects
                // this app's own keep/hide balance rather than the global mix.
                val hideDocs = global.count { !it.keep } + app.count { !it.keep } * APP_WEIGHT.toInt()
                val keepDocs = global.count { it.keep } + app.count { it.keep } * APP_WEIGHT.toInt()

                return Model(
                    hide = hide,
                    keep = keep,
                    hideTotal = hide.values.sum(),
                    keepTotal = keep.values.sum(),
                    vocabulary = vocabulary.size.coerceAtLeast(1),
                    hideDocs = hideDocs,
                    keepDocs = keepDocs,
                )
            }

            /** Strips the internal prefixes so the log reads like words, not like features. */
            fun readable(feature: String): String = feature
                .removePrefix("w:")
                .removePrefix("ti:")
                .replace("ch:", "channel ")
                .replace("«", "")
                .replace("»", "")
        }
    }

    companion object {
        /**
         * Log-odds needed before the model may act without asking. 4.0 is roughly
         * 98% posterior — in practice it is cleared by templates that are trivial
         * variants of ones already labelled, which is exactly the tail the
         * deliberately over-splitting fingerprinter creates.
         */
        const val DEFAULT_MARGIN = 4.0

        /** No autonomous verdicts at all until the user has taught it this much. */
        const val DEFAULT_MIN_EXAMPLES = 8
    }
}
