package com.jarvisai.app.api.context

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var foregroundApp: String = "com.jarvisai.app"

    fun updateForegroundApp(packageName: String) {
        foregroundApp = packageName
    }

    fun getCurrentContext(): String {
        return buildString {
            append("--- CURRENT SYSTEM CONTEXT ---\n")
            append("Time: ${getTimeOfDay()} (${Calendar.getInstance().time})\n")
            append("Battery: ${getBatteryLevel()}%\n")
            append("Foreground App: $foregroundApp\n")
            append("Screen Content Access: ${isAccessibilityActive()}\n")
        }
    }

    private fun isAccessibilityActive(): Boolean {
        return com.jarvisai.app.service.JarvisAccessibilityService.instance != null
    }

    private fun getTimeOfDay(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..20 -> "Evening"
            else -> "Night"
        }
    }

    private fun getBatteryLevel(): Int {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level == -1 || scale == -1) 50 else (level * 100 / scale.toFloat()).toInt()
    }
}
