package com.yk.notiffilter.data

import com.yk.notiffilter.classify.Labelled
import com.yk.notiffilter.classify.Tokenizer
import com.yk.notiffilter.classify.TrainingSource

/**
 * Re-derives the training set from the verdict table on every call.
 *
 * Nothing is cached or materialised. The model is cheap enough to rebuild from a few
 * dozen rows that storing counts would only buy a way for them to fall out of step
 * with the labels the user can see and edit in the UI.
 */
class VerdictTrainingSource(private val dao: TemplateVerdictDao) : TrainingSource {

    override suspend fun forApp(packageName: String): List<Labelled> =
        dao.trainingForApp(packageName).map(::toLabelled)

    override suspend fun global(): List<Labelled> =
        dao.trainingGlobal().map(::toLabelled)

    private fun toLabelled(row: TemplateVerdict) = Labelled(
        features = Tokenizer.features(
            channelId = row.channelId.takeIf { it.isNotBlank() },
            title = row.sampleTitle,
            text = row.sampleText,
        ),
        keep = row.verdict == Verdict.KEEP,
    )
}
