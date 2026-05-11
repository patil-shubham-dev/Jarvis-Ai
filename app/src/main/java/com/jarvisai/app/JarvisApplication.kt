package com.jarvisai.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class JarvisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "jarvis_service",
                "Jarvis System Status",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows if Jarvis Vision and Controls are active"
            }
            val manager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
