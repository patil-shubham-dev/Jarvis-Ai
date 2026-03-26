package com.jarvis.assistant.core.prefs

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(v) = prefs.edit().putString(KEY_API_KEY, v).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = prefs.edit().putString(KEY_MODEL, v).apply()

    var ttsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS, true)
        set(v) = prefs.edit().putBoolean(KEY_TTS, v).apply()

    var ttsSpeed: Float
        get() = prefs.getFloat(KEY_TTS_SPEED, 0.95f)
        set(v) = prefs.edit().putFloat(KEY_TTS_SPEED, v).apply()

    var ttsPitch: Float
        get() = prefs.getFloat(KEY_TTS_PITCH, 0.9f)
        set(v) = prefs.edit().putFloat(KEY_TTS_PITCH, v).apply()

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_WORD, true)
        set(v) = prefs.edit().putBoolean(KEY_WAKE_WORD, v).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING, false)
        set(v) = prefs.edit().putBoolean(KEY_ONBOARDING, v).apply()

    var currentSessionId: String
        get() = prefs.getString(KEY_SESSION, "") ?: ""
        set(v) = prefs.edit().putString(KEY_SESSION, v).apply()

    // Runtime-only, not persisted
    var ttsSessionEnabled: Boolean = true
    var vaultUnlocked: Boolean = false

    fun newSession(): String {
        val id = "s_${System.currentTimeMillis()}"
        currentSessionId = id
        return id
    }

    companion object {
        private const val KEY_API_KEY   = "api_key"
        private const val KEY_MODEL     = "model"
        private const val KEY_TTS       = "tts"
        private const val KEY_TTS_SPEED = "tts_speed"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_WAKE_WORD = "wake_word"
        private const val KEY_ONBOARDING = "onboarding_done"
        private const val KEY_SESSION   = "session_id"
        const val DEFAULT_MODEL         = "claude-opus-4-5"
    }
}
