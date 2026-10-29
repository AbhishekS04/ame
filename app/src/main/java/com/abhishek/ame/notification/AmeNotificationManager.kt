package com.abhishek.ame.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.abhishek.ame.AmeApplication
import com.abhishek.ame.R
import com.abhishek.ame.ui.MainActivity
import java.util.Calendar

object AmeNotificationManager {

    const val CHANNEL_UPDATES = "ame_channel_updates"
    const val CHANNEL_MUSIC = "ame_channel_music"

    const val ID_UPDATE = 1001
    const val ID_MUSIC = 2001
    const val ID_DROP = 2002

    const val ACTION_OPEN_UPDATES = "com.abhishek.ame.OPEN_UPDATES"
    const val EXTRA_OPEN_ROUTE = "open_route"

    private const val PREFS_NAME = "ame_notification_state"
    private const val KEY_LAST_UPDATE_NOTIFIED = "last_update_notified"
    private const val KEY_LAST_MUSIC_NOTIFIED = "last_music_notified"

    fun init(context: Context) {
        createChannels(context)
        scheduleMusicReminder(context)
    }

    private fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val updatesChannel = NotificationChannel(
                CHANNEL_UPDATES,
                "App Updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications when a new version of Ame is available"
                setShowBadge(true)
            }

            val musicChannel = NotificationChannel(
                CHANNEL_MUSIC,
                "Music & Recommendations",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Daily vibes, new drops, and listening suggestions"
                setShowBadge(true)
            }

            nm.createNotificationChannel(updatesChannel)
            nm.createNotificationChannel(musicChannel)
        }
    }

    /**
     * Notifies the user that a new app update is ready to install.
     */
    fun notifyUpdate(context: Context, version: String, notes: String) {
        val app = context.applicationContext as? AmeApplication
        val prefsStore = app?.preferences
        if (prefsStore?.notificationsEnabled?.value == false) return

        val statePrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotified = statePrefs.getString(KEY_LAST_UPDATE_NOTIFIED, null)
        if (lastNotified == version) {
            return
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_UPDATES
            putExtra(EXTRA_OPEN_ROUTE, "updates")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getActivity(context, 101, intent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Update Available • Ame v$version")
            .setContentText("A new version of Ame is ready to install. Tap to update.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                if (notes.isNotBlank()) "Ame v$version is available.\n\n$notes" else "A new version of Ame (v$version) is ready to install. Tap to view release notes."
            ))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(ID_UPDATE, notification)
        statePrefs.edit().putString(KEY_LAST_UPDATE_NOTIFIED, version).apply()
    }

    /**
     * Notifies the user when fresh albums, playlists, or community mixes drop.
     */
    fun notifyMusicDrop(context: Context, title: String, message: String, force: Boolean = false) {
        val app = context.applicationContext as? AmeApplication
        val prefsStore = app?.preferences
        if (prefsStore?.notificationsEnabled?.value == false || prefsStore?.musicRemindersEnabled?.value == false) return

        val statePrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotified = statePrefs.getLong(KEY_LAST_MUSIC_NOTIFIED, 0L)
        val now = System.currentTimeMillis()
        if (!force && (now - lastNotified) < 18 * 3600 * 1000L) {
            return
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getActivity(context, 102, intent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_MUSIC)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(ID_DROP, notification)
        statePrefs.edit().putLong(KEY_LAST_MUSIC_NOTIFIED, now).apply()
    }

    /**
     * Schedules periodic music reminder alarms.
     */
    fun scheduleMusicReminder(context: Context, delayHours: Long = 8) {
        val app = context.applicationContext as? AmeApplication
        val prefsStore = app?.preferences
        if (prefsStore?.notificationsEnabled?.value == false || prefsStore?.musicRemindersEnabled?.value == false) return

        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, MusicNotificationReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getBroadcast(context, 201, intent, flags)

        val triggerTime = System.currentTimeMillis() + (delayHours * 3600 * 1000L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            am.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun cancelMusicReminder(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, MusicNotificationReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pendingIntent = PendingIntent.getBroadcast(context, 201, intent, flags)
        am.cancel(pendingIntent)
    }
}
