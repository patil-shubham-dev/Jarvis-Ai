package com.jarvis.assistant.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        var instance: JarvisAccessibilityService? = null
        fun isRunning() = instance != null
    }

    override fun onServiceConnected() {
        instance = this
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        Log.d("JarvisA11y", "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun tapSendButton() {
        val root = rootInActiveWindow ?: return
        val whatsappSend = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
        if (!whatsappSend.isNullOrEmpty()) {
            whatsappSend.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            listOf("Send", "send").forEach { label ->
                root.findAccessibilityNodeInfosByText(label)
                    ?.firstOrNull()
                    ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        root.recycle()
    }

    fun setVolume(percent: Int) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (percent / 100.0 * max).toInt().coerceIn(0, max)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    fun setBrightness(percent: Int) {
        try {
            val value = (percent / 100.0 * 255).toInt().coerceIn(0, 255)
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
        } catch (e: Exception) {
            Log.w("JarvisA11y", "Brightness requires WRITE_SETTINGS — grant via ADB")
        }
    }
}
