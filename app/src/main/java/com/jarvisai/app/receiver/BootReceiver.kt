package com.jarvisai.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import com.jarvisai.app.service.JarvisOverlayService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs: SharedPreferences =
            context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        val overlayEnabled = prefs.getBoolean("overlay_enabled", false)
        if (overlayEnabled) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, JarvisOverlayService::class.java)
            )
        }
    }
}
