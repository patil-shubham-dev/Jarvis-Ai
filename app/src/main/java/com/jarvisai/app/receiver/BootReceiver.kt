package com.jarvisai.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.jarvisai.app.service.JarvisOverlayService
import com.jarvisai.app.utils.SecurePrefs

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
    }
}
