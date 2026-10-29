package com.abhishek.ame.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.abhishek.ame.AmeApplication
import com.abhishek.ame.R
import com.abhishek.ame.ui.MainActivity
import java.util.Calendar
import kotlin.random.Random

class MusicNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as? AmeApplication
        val prefs = app?.preferences
        if (prefs?.notificationsEnabled?.value == false || prefs?.musicRemindersEnabled?.value == false) {
            return
        }

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val (title, body) = when (hour) {
            in 6..11 -> Pair(
                "Morning Soundtrack ☀️",
                "Start your morning right with crisp tunes and high-fidelity sound.",
            )
            in 12..16 -> Pair(
                "Afternoon Focus ⚡",
                "Recharge your energy with tailored recommendations on Ame.",
            )
            in 17..21 -> Pair(
                "Evening Vibes 🎧",
                "Time to unwind. Your personalized playlist is queued up and ready.",
            )
            else -> Pair(
                "Late Night Session 🌙",
                "Smooth beats and soothing ambient music for the night.",
            )
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (nm != null) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val pendingIntent = PendingIntent.getActivity(context, 202, launchIntent, flags)

            val notification = NotificationCompat.Builder(context, AmeNotificationManager.CHANNEL_MUSIC)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            nm.notify(AmeNotificationManager.ID_MUSIC, notification)
        }

        // Reschedule next reminder in 8 to 14 hours with random jitter
        val nextDelayHours = 8L + Random.nextLong(0, 6)
        AmeNotificationManager.scheduleMusicReminder(context, nextDelayHours)
    }
}
