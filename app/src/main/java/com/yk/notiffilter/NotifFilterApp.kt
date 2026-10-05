package com.yk.notiffilter

import android.app.Application
import com.yk.notiffilter.classify.NaiveBayesClassifier
import com.yk.notiffilter.classify.NotificationClassifier
import com.yk.notiffilter.data.NotifFilterDatabase
import com.yk.notiffilter.data.VerdictTrainingSource
import com.yk.notiffilter.notify.KeeperReposter
import com.yk.notiffilter.service.FilterEngine
import com.yk.notiffilter.rule.DefaultRules
import com.yk.notiffilter.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotifFilterApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                DefaultRules.preseedIfEmpty(db)
            }
        }
    }

    val db: NotifFilterDatabase by lazy { NotifFilterDatabase.get(this) }
    val settings: AppSettings by lazy { AppSettings(this) }
    val reposter: KeeperReposter by lazy { KeeperReposter(this) }
    val repository: FilterRepository by lazy { FilterRepository(db, settings, reposter) }

    val filterEngine: FilterEngine by lazy { FilterEngine(db, ::classifier) }

    /**
     * Built fresh each call so a threshold change in Settings takes effect on the
     * next notification rather than the next app restart. The model itself is
     * rebuilt from the verdict table per classification, so there is no state here
     * that could go stale.
     */
    fun classifier(): NotificationClassifier = NaiveBayesClassifier(
        training = VerdictTrainingSource(db.verdicts()),
        confidenceMargin = settings.confidenceMargin.toDouble(),
        minExamples = settings.minExamples,
    )
}
