package com.jarvisai.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.jarvisai.app.data.local.AppDatabase
import com.jarvisai.app.notifications.ReminderScheduler
import com.jarvisai.app.service.JarvisOverlayService
import com.jarvisai.app.utils.SecurePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val overlayEnabled = SecurePrefs.isOverlayEnabled(context)
        if (overlayEnabled) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, JarvisOverlayService::class.java)
            )
        }

        if (SecurePrefs.isVoiceIntelligenceEnabled(context)) {
            val voiceIntent = Intent(context, com.jarvisai.app.service.JarvisBackgroundService::class.java).apply {
                putExtra("PICOVOICE_ACCESS_KEY", SecurePrefs.getPicovoiceKey(context))
            }
            ContextCompat.startForegroundService(context, voiceIntent)
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val scheduler = ReminderScheduler(context.applicationContext)
                database.reminderDao().getPendingReminders().first().forEach { reminder ->
                    scheduler.schedule(reminder.id, reminder.triggerAtMillis)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
