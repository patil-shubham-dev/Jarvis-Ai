package com.jarvisai.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jarvisai.app.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val reminderId = intent?.getLongExtra(EXTRA_REMINDER_ID, -1L) ?: -1L
        if (reminderId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                JarvisNotificationManager.ensureChannels(context)
                val database = AppDatabase.getInstance(context)

                val reminder = database.reminderDao().getById(reminderId) ?: return@launch
                JarvisNotificationManager.showReminderNotification(
                    context = context,
                    notificationId = reminderId.toInt(),
                    title = reminder.title,
                    message = reminder.message
                )
                database.reminderDao().markCompleted(reminderId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
    }
}
