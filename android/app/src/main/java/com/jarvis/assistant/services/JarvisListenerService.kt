package com.jarvis.assistant.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvis.assistant.core.prefs.Prefs
import com.jarvis.assistant.ui.chat.ChatActivity

class JarvisListenerService : Service() {

    private var recognizer: SpeechRecognizer? = null
    private lateinit var prefs: Prefs

    companion object {
        const val CHANNEL_ID = "jarvis_bg"
        const val ACTION_WAKE = "com.jarvis.WAKE_DETECTED"
        var isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        isRunning = true
        createNotificationChannel()
        startForeground(1, buildNotification())
        if (prefs.wakeWordEnabled) startListening()
    }

    override fun onDestroy() {
        isRunning = false
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: android.os.Bundle?) {
                val heard = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.lowercase() ?: ""
                if (heard.contains("jarvis")) {
                    Log.d("WakeWord", "Triggered: $heard")
                    sendBroadcast(Intent(ACTION_WAKE))
                }
                recognizer?.startListening(intent)
            }
            override fun onError(error: Int) { recognizer?.startListening(intent) }
            override fun onReadyForSpeech(p: android.os.Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(v: Float) = Unit
            override fun onBufferReceived(b: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(r: android.os.Bundle?) = Unit
            override fun onEvent(t: Int, p: android.os.Bundle?) = Unit
        })
        recognizer?.startListening(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Jarvis Assistant", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Wake word listener"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Jarvis")
        .setContentText("Say \"Jarvis\" to activate")
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, ChatActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()
}
