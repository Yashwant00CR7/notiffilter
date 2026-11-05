package com.yk.notiffilter.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.yk.notiffilter.NotifFilterApp
import com.yk.notiffilter.fingerprint.Fingerprinter
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
        instance = this
        connected = true
        Log.i(TAG, "Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) instance = null
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

    /**
     * Keys this service cancelled itself, waiting for their [onNotificationRemoved].
     *
     * Bounded and least-recently-used, not cleared wholesale. The previous version
     * emptied the whole set once it passed 200 entries, which dropped the guards for
     * every cancel still in flight — and the removal callback for one of those then
     * read as "the user dismissed the original" and cancelled the keeper we had just
     * posted. The notification vanished a fraction of a second after appearing.
     */
    private val internallyCancelledKeys: MutableSet<String> =
        java.util.Collections.synchronizedSet(
            object : java.util.LinkedHashSet<String>() {
                override fun add(element: String): Boolean {
                    val added = super.add(element)
                    if (size > MAX_TRACKED_KEYS) iterator().let { it.next(); it.remove() }
                    return added
                }
            }
        )

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.packageName == packageName) return

        // If we cancelled the source notification ourselves (e.g. to replace it with
        // our loud repost or to hide it), do NOT cancel our active repost!
        if (internallyCancelledKeys.remove(notification.key)) {
            return
        }

        // Dismissing the source notification externally should take our copy with it,
        // so the two never drift apart in the shade.
        reposter.cancelRepost(notification.key)
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        internallyCancelledKeys.clear()
        scope.cancel()
        super.onDestroy()
    }

    override fun cancel(key: String) {
        internallyCancelledKeys.add(key)
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

    override fun nudge(pendingCount: Int, appLabels: List<String>) {
        reposter.nudge(pendingCount, appLabels)
    }

    override fun clearNudge() = reposter.clearNudge()

    fun dismissMatching(
        packageName: String,
        fingerprint: String? = null,
        title: String? = null,
        text: String? = null,
    ) {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        for (sbn in active) {
            if (sbn == null) continue
            if (sbn.packageName == packageName) {
                val incoming = engine.extract(sbn)
                val matches = when {
                    incoming != null && fingerprint != null -> {
                        val fp = Fingerprinter.fingerprint(
                            incoming.packageName,
                            incoming.channelId,
                            incoming.title,
                            incoming.text,
                        )
                        fp == fingerprint
                    }
                    incoming != null -> {
                        (title.isNullOrBlank() || incoming.title == title) &&
                            (text.isNullOrBlank() || incoming.text == text)
                    }
                    else -> {
                        val extras = sbn.notification?.extras
                        val t = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
                        val m = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
                        (title.isNullOrBlank() || t == title) && (text.isNullOrBlank() || m == text)
                    }
                }
                if (matches) {
                    cancel(sbn.key)
                    reposter.cancelRepost(sbn.key)
                }
            } else if (sbn.packageName == this.packageName) {
                val extras = sbn.notification?.extras
                val repTitle = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
                val repText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                    ?: extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
                if ((!title.isNullOrBlank() && repTitle == title) || (!text.isNullOrBlank() && repText == text)) {
                    cancel(sbn.key)
                }
            }
        }
    }

    companion object {
        private const val TAG = "NotifFilter"

        /** Enough to cover every cancel plausibly in flight at once. */
        private const val MAX_TRACKED_KEYS = 200

        @Volatile
        var instance: FilterListenerService? = null
            private set

        @Volatile
        var connected: Boolean = false
            private set

        fun dismissMatching(
            packageName: String,
            fingerprint: String? = null,
            title: String? = null,
            text: String? = null,
        ) {
            instance?.dismissMatching(packageName, fingerprint, title, text)
        }

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
