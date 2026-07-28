package com.jarvisai.app.core.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
class VoiceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val initLatch = CountDownLatch(1)
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    init {
        tts = TextToSpeech(context, this)
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.UK)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("VoiceEngine", "Language not supported")
            } else {
                tts?.setPitch(0.85f)
                tts?.setSpeechRate(1.05f)
                
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onDone(utteranceId: String?) {
                        abandonAudioFocus()
                    }
                    override fun onError(utteranceId: String?) {
                        abandonAudioFocus()
                    }
                    override fun onStart(utteranceId: String?) {}
                })
                
                isInitialized = true
            }
        }
        initLatch.countDown()
    }

    suspend fun speak(text: String): Boolean = suspendCoroutine { continuation ->
        if (!isInitialized) {
            val ready = initLatch.await(2, TimeUnit.SECONDS)
            if (!ready || !isInitialized) {
                Log.e("VoiceEngine", "TTS not initialized in time")
                continuation.resume(false)
                return@suspendCoroutine
            }
        }
        
        requestAudioFocus()
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_speech")
        continuation.resume(result == TextToSpeech.SUCCESS)
    }

    fun speakNow(text: String) {
        if (isInitialized) {
            requestAudioFocus()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_speech")
        } else {
            val ready = initLatch.await(2, TimeUnit.SECONDS)
            if (ready && isInitialized) {
                requestAudioFocus()
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_speech")
            }
        }
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking ?: false

    fun stop() {
        tts?.stop()
        abandonAudioFocus()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        abandonAudioFocus()
    }

    private fun requestAudioFocus() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioFocusRequest = focusRequest
                audioManager?.requestAudioFocus(focusRequest)
            } else {
                audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        } catch (e: Exception) {
            Log.e("VoiceEngine", "Failed to request audio focus: ${e.message}")
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                audioManager?.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.e("VoiceEngine", "Failed to abandon audio focus: ${e.message}")
        }
    }
}
