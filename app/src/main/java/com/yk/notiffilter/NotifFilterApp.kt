package com.yk.notiffilter

import android.app.Application
import com.yk.notiffilter.classify.GroqClassifier
import com.yk.notiffilter.classify.NotificationClassifier
import com.yk.notiffilter.data.NotifFilterDatabase
import com.yk.notiffilter.notify.KeeperReposter
import com.yk.notiffilter.service.FilterEngine
import com.yk.notiffilter.settings.SecureSettings

class NotifFilterApp : Application() {

    val db: NotifFilterDatabase by lazy { NotifFilterDatabase.get(this) }
    val settings: SecureSettings by lazy { SecureSettings(this) }
    val reposter: KeeperReposter by lazy { KeeperReposter(this) }
    val repository: FilterRepository by lazy { FilterRepository(db, settings) }

    val filterEngine: FilterEngine by lazy {
        FilterEngine(db, settings, ::classifier)
    }

    /**
     * Built per call rather than cached so an API key or model change in Settings
     * takes effect on the next notification instead of the next app restart.
     */
    fun classifier(): NotificationClassifier? {
        val key = settings.apiKey
        if (key.isBlank()) return null
        return GroqClassifier(apiKey = key, model = settings.model, baseUrl = settings.baseUrl)
    }
}
