package com.yk.notiffilter.settings

import android.content.Context
import com.yk.notiffilter.classify.NaiveBayesClassifier

/**
 * Plain preferences. There is nothing secret left to store.
 *
 * This replaced a Keystore-backed AES-GCM wrapper that existed solely to hold a Groq
 * API key. With classification on-device and INTERNET removed from the manifest,
 * there is no credential, no endpoint and no model id — so the encryption it was
 * protecting them with had nothing left to protect.
 */
class AppSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("notiffilter_settings", Context.MODE_PRIVATE)

    /**
     * Log-odds the classifier must clear before it may act without asking. Raising it
     * means more questions and fewer surprises; lowering it means the reverse.
     */
    var confidenceMargin: Float
        get() = prefs.getFloat(KEY_MARGIN, NaiveBayesClassifier.DEFAULT_MARGIN.toFloat())
        set(value) = prefs.edit().putFloat(KEY_MARGIN, value.coerceIn(1f, 12f)).apply()

    /** Labels required before the model is allowed any autonomy at all. */
    var minExamples: Int
        get() = prefs.getInt(KEY_MIN_EXAMPLES, NaiveBayesClassifier.DEFAULT_MIN_EXAMPLES)
        set(value) = prefs.edit().putInt(KEY_MIN_EXAMPLES, value.coerceIn(0, 100)).apply()

    /**
     * One-shot guard for the shipped rule set.
     *
     * Without it the seeder re-added every default the user had deleted, on every
     * launch, so deleting a rule never stuck.
     */
    var defaultsSeeded: Boolean
        get() = prefs.getBoolean(KEY_SEEDED, false)
        set(value) = prefs.edit().putBoolean(KEY_SEEDED, value).apply()

    var logRetentionDays: Int
        get() = prefs.getInt(KEY_RETENTION, 30)
        set(value) = prefs.edit().putInt(KEY_RETENTION, value.coerceIn(1, 365)).apply()

    private companion object {
        const val KEY_MARGIN = "confidence_margin"
        const val KEY_MIN_EXAMPLES = "min_examples"
        const val KEY_RETENTION = "log_retention_days"
        const val KEY_SEEDED = "defaults_seeded"
    }
}
