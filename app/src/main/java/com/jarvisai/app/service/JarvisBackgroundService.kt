package com.jarvisai.app.service

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jarvisai.app.R
import com.jarvisai.app.ui.chat.MainActivity

/**
 * Service that runs continuously in the background to listen for "Hey Jarvis" 
 * wake word using Picovoice Porcupine without draining battery significantly.
 */
class JarvisBackgroundService : Service() {

    private var porcupine: ai.picovoice.porcupine.Porcupine? = null
    private var audioRecord: android.media.AudioRecord? = null
    private val isListening = java.util.concurrent.atomic.AtomicBoolean(false)
    private var recordThread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val accessKey = intent?.getStringExtra("PICOVOICE_ACCESS_KEY") 
            ?: com.jarvisai.app.util.SecurePrefs.getPicovoiceKey(this)

        if (accessKey.isNotEmpty()) {
            startCustomHotwordDetection(accessKey)
        } else {
            Log.e(TAG, "Cannot start Porcupine: AccessKey is empty.")
        }
        
        return START_STICKY
    }

    private fun startCustomHotwordDetection(accessKey: String) {
        if (isListening.get()) return

        try {
            porcupine = ai.picovoice.porcupine.Porcupine.Builder()
                .setAccessKey(accessKey)
                .setKeyword(ai.picovoice.porcupine.Porcupine.BuiltInKeyword.PORCUPINE)
                .build(applicationContext)

            val minBufferSize = android.media.AudioRecord.getMinBufferSize(
                porcupine!!.sampleRate,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT
            )

            // We explicitly use MIC instead of VOICE_RECOGNITION to not suppress background audio apps
            audioRecord = android.media.AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                porcupine!!.sampleRate,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize.coerceAtLeast(porcupine!!.frameLength * 2)
            )

            if (audioRecord?.state != android.media.AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize.")
                return
            }

            audioRecord?.startRecording()
            isListening.set(true)

            recordThread = Thread {
                val pcmFrame = ShortArray(porcupine!!.frameLength)
                while (isListening.get()) {
                    val readResult = audioRecord?.read(pcmFrame, 0, pcmFrame.size) ?: 0
                    if (readResult == pcmFrame.size) {
                        try {
                            val keywordIndex = porcupine?.process(pcmFrame) ?: -1
                            if (keywordIndex >= 0) {
                                onWakeWordDetected()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Porcupine processing error: ${e.message}")
                        }
                    }
                }
            }
            recordThread?.priority = Thread.MAX_PRIORITY
            recordThread?.start()

            Log.d(TAG, "Custom AudioRecord hotword detection started successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start custom Hotword detection: ${e.localizedMessage}")
        }
    }

    private fun onWakeWordDetected() {
        Log.i(TAG, "Wake word detected!")
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("WAKE_WORD_TRIGGERED", true)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isListening.set(false)
        try {
            audioRecord?.stop()
            audioRecord?.release()
            porcupine?.delete()
            audioRecord = null
            porcupine = null
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Jarvis Hotword Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Listening for wake word" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_mic)
        .setContentTitle("Jarvis Listening...")
        .setContentText("Say 'Porcupine' to wake me up.")
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE)
        )
        .build()

    companion object {
        private const val TAG = "JarvisBackgroundService"
        private const val CHANNEL_ID = "jarvis_hotword"
        private const val NOTIF_ID = 2002
    }
}
