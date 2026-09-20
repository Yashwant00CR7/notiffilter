package com.yk.notiffilter.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.yk.notiffilter.NotifFilterApp
import com.yk.notiffilter.notify.KeeperReposter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FilterListenerService : NotificationListenerService(), FilterHost {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var engine: FilterEngine
    private lateinit var reposter: KeeperReposter

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as NotifFilterApp
        engine = app.filterEngine
        reposter = app.reposter
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        Log.i(TAG, "Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected = false
        // ColorOS is aggressive about killing bound services. Asking for a rebind is
        // the documented recovery; a listener that stays dead degrades to "everything
        // shows", which is safe but useless.
        Log.w(TAG, "Listener disconnected; requesting rebind")
        runCatching { requestRebind(ComponentName(this, FilterListenerService::class.java)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.packageName == packageName) return // never filter our own re-posts

        scope.launch {
            runCatching { engine.onPosted(notification, this@FilterListenerService) }
                .onFailure { Log.e(TAG, "Filtering failed for ${notification.packageName}", it) }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Dismissing the source notification should take our copy with it, so the two
        // never drift apart in the shade.
        sbn?.takeIf { it.packageName != packageName }?.let { reposter.cancelRepost(it.key) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun cancel(key: String) {
        runCatching { cancelNotification(key) }
            .onFailure { Log.e(TAG, "cancelNotification failed", it) }
    }

    override fun repost(
        sbn: StatusBarNotification,
        appLabel: String,
        title: String,
        text: String,
    ) {
        reposter.repost(sbn, appLabel, title, text)
    }

    companion object {
        private const val TAG = "NotifFilter"

        @Volatile
        var connected: Boolean = false
            private set

        /** Source of truth for whether the user has granted notification access. */
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            return flat.split(':').any {
                ComponentName.unflattenFromString(it)?.packageName == context.packageName
            }
        }
    }
}
