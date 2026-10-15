package com.isaac.ridelab.locale

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.isaac.ridelab.R

/** Presentation-only refresh on Android 12; no service commands or ride state ownership. */
internal object LocalizedRideNotification {
    fun refresh(context: Context, previous: Context, localized: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        try {
            val titles = listOf(R.string.ride_active, R.string.ride_paused, R.string.ride_devices_retained)
            val texts = listOf(R.string.ride_notification_text, R.string.ride_devices_retained_hint)
            val actions = listOf(R.string.ride_end, R.string.workout_open)
            fun translated(text: CharSequence?, ids: List<Int>): CharSequence? =
                ids.firstOrNull { previous.getString(it) == text?.toString() }?.let(localized::getString) ?: text
            manager.activeNotifications.forEach { posted ->
                val notification = posted.notification
                if (titles.none { previous.getString(it) == notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() })
                    return@forEach
                manager.getNotificationChannel(notification.channelId)?.let { channel ->
                    channel.name = localized.getString(R.string.ride_channel)
                    manager.createNotificationChannel(channel)
                }
                val builder = Notification.Builder.recoverBuilder(context, notification)
                    .setContentTitle(translated(notification.extras.getCharSequence(Notification.EXTRA_TITLE), titles))
                    .setContentText(translated(notification.extras.getCharSequence(Notification.EXTRA_TEXT), texts))
                    .setOnlyAlertOnce(true)
                notification.actions?.let { existing -> builder.setActions(*existing.map { action ->
                    Notification.Action.Builder(action.getIcon(), translated(action.title, actions), action.actionIntent).build()
                }.toTypedArray()) }
                manager.notify(posted.tag, posted.id, builder.build())
            }
        } catch (error: RuntimeException) {
            Log.w("RideLabLocale", "Notification language refresh failed; ride continues unchanged", error)
        }
    }
}
