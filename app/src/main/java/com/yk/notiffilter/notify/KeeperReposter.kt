package com.yk.notiffilter.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import com.yk.notiffilter.R
import com.yk.notiffilter.ui.MainActivity

/**
 * Re-posts kept notifications on our own high-importance channel, and owns the one
 * other notification this app ever posts: the silent label-queue nudge.
 *
 * This is what actually solves the problem. By the time a listener sees a
 * notification the phone has already buzzed, so hiding junk after the fact only
 * cleans up the shade. The fix is to silence the source app's channels once, in
 * Settings, and let this class supply the sound for keepers only.
 */
class KeeperReposter(private val context: Context) {

    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Kept notifications",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notifications NotifFilter decided were worth interrupting you for."
                enableVibration(true)
            }
        )

        // Deliberately IMPORTANCE_LOW: an app whose purpose is fewer interruptions
        // must not interrupt you to ask a question. The queue waits until you look.
        manager.createNotificationChannel(
            NotificationChannel(
                QUEUE_CHANNEL_ID,
                "Templates awaiting your verdict",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "A silent reminder that NotifFilter has questions for you."
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    /**
     * One batched, silent summary rather than a prompt per notification.
     *
     * In the first days of a new app almost everything is unsure, so per-item prompts
     * would mean twenty degraded copies in the shade. This collapses the whole backlog
     * into a single row that leads to the bulk-label screen.
     */
    fun nudge(pendingCount: Int, appLabels: List<String>) {
        val subject = if (pendingCount == 1) "template needs" else "templates need"
        val builder = Notification.Builder(context, QUEUE_CHANNEL_ID)
            .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_notification))
            .setContentTitle("$pendingCount $subject your verdict")
            .setContentText(appLabels.distinct().joinToString(" · ").ifBlank { "Tap to review" })
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(queueIntent())

        manager.notify(QUEUE_NOTIFICATION_ID, builder.build())
    }

    fun clearNudge() = manager.cancel(QUEUE_NOTIFICATION_ID)

    private fun queueIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_QUEUE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            QUEUE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Returns the id of the posted notification.
     *
     * Callers must post before cancelling the original. If this throws, the silent
     * original is still sitting in the shade, so a failure costs a buzz and never a
     * message.
     */
    fun repost(sbn: StatusBarNotification, appLabel: String, title: String, text: String): Int {
        val source = sbn.notification
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon(source))
            .setContentTitle(title.ifBlank { appLabel })
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSubText(appLabel)
            .setWhen(sbn.postTime)
            .setShowWhen(true)
            .setAutoCancel(true)

        // Forwarding the original PendingIntent is what keeps tap-through working:
        // tapping our copy still opens the source app's own screen for this event.
        source.contentIntent?.let { builder.setContentIntent(it) }
        appIcon(sbn.packageName)?.let { builder.setLargeIcon(it) }

        val id = notificationId(sbn.key)
        manager.notify(id, builder.build())
        return id
    }

    fun cancelRepost(key: String) = manager.cancel(notificationId(key))

    /**
     * An Icon carries its own source package, so the platform can usually resolve
     * another app's small icon. Falls back to our own monochrome glyph when it
     * cannot, since a notification with an unresolvable icon does not render at all.
     */
    private fun smallIcon(source: Notification): Icon =
        source.smallIcon ?: Icon.createWithResource(context, R.drawable.ic_notification)

    private fun appIcon(packageName: String): Icon? = runCatching {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        Icon.createWithBitmap(drawable.toBitmap())
    }.getOrNull()

    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable && bitmap != null) return bitmap
        val width = intrinsicWidth.takeIf { it > 0 } ?: 128
        val height = intrinsicHeight.takeIf { it > 0 } ?: 128
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }

    /**
     * Stable per source notification, so an update replaces our copy rather than
     * stacking. Nudged off [QUEUE_NOTIFICATION_ID] on the off chance a key hashes to
     * it — a collision there would have a re-post silently cancel the label queue.
     */
    private fun notificationId(key: String): Int =
        key.hashCode().let { if (it == QUEUE_NOTIFICATION_ID) it + 1 else it }

    private companion object {
        const val CHANNEL_ID = "notiffilter_keepers"
        const val QUEUE_CHANNEL_ID = "notiffilter_queue"

        /** Fixed, so the nudge replaces itself instead of stacking. */
        const val QUEUE_NOTIFICATION_ID = 1
    }
}
