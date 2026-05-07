package com.jarvisai.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jarvisai.app.R
import com.jarvisai.app.ui.activities.MainActivity

object JarvisNotificationManager {
    const val CHANNEL_SERVICES = "jarvis_services"
    const val CHANNEL_REMINDERS = "jarvis_reminders"
    const val CHANNEL_STATUS = "jarvis_status"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = listOf(
            NotificationChannel(
                CHANNEL_SERVICES,
                "Jarvis Services",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground notifications for Jarvis background features."
            },
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Jarvis Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Smart reminders, task alerts, and follow-ups from Jarvis."
                enableVibration(true)
            },
            NotificationChannel(
                CHANNEL_STATUS,
                "Jarvis Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General progress and task-completion updates from Jarvis."
            }
        )
        manager.createNotificationChannels(channels)
    }

    fun buildServiceNotification(
        context: Context,
        title: String,
        text: String,
        channelId: String = CHANNEL_SERVICES,
        iconRes: Int = R.drawable.ic_notification
    ) = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(iconRes)
        .setContentTitle(title)
        .setContentText(text)
        .setOngoing(true)
        .setContentIntent(mainActivityPendingIntent(context))
        .build()

    fun showReminderNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String
    ) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun showStatusNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String
    ) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(mainActivityPendingIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun mainActivityPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
