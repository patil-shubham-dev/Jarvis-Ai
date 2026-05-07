package com.jarvisai.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jarvisai.app.notifications.JarvisNotificationManager

class JarvisNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (packageName == "com.jarvisai.app") return // Ignore ourselves

        Log.d("JarvisNotification", "New notification from $packageName: $title - $text")
        
        // Future: Smart Summary and Reply logic
        // For now, we just log it to the intelligence engine
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Notification dismissed
    }
}
