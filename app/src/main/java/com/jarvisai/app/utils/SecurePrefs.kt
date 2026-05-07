package com.jarvisai.app.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure, encrypted preferences store using Android Keystore + AES-256.
 * Used for API keys, biometric settings, and all sensitive config.
 */
object SecurePrefs {

    private const val FILE_NAME = "jarvis_secure_prefs"

    private var sharedPrefs: SharedPreferences? = null

    @Synchronized
    private fun get(context: Context): SharedPreferences {
        if (sharedPrefs == null) {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            sharedPrefs = EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
        return sharedPrefs!!
    }

    fun saveApiKey(context: Context, key: String) =
        get(context).edit().putString(KEY_API_KEY, key.trim()).apply()

    fun getApiKey(context: Context): String =
        get(context).getString(KEY_API_KEY, "") ?: ""

    fun saveProvider(context: Context, provider: String) =
        get(context).edit().putString(KEY_PROVIDER, provider).apply()

    fun getProvider(context: Context): String =
        get(context).getString(KEY_PROVIDER, "") ?: ""

    fun saveBaseUrl(context: Context, url: String) =
        get(context).edit().putString(KEY_BASE_URL, url.trim()).apply()

    fun getBaseUrl(context: Context): String =
        get(context).getString(KEY_BASE_URL, "https://api.openai.com/v1/") ?: "https://api.openai.com/v1/"

    fun saveBiometricEnabled(context: Context, enabled: Boolean) =
        get(context).edit().putBoolean(KEY_BIOMETRIC, enabled).apply()

    fun isBiometricEnabled(context: Context): Boolean =
        get(context).getBoolean(KEY_BIOMETRIC, true)

    fun saveTtsEnabled(context: Context, enabled: Boolean) =
        get(context).edit().putBoolean(KEY_TTS, enabled).apply()

    fun isTtsEnabled(context: Context): Boolean =
        get(context).getBoolean(KEY_TTS, false)

    fun saveOverlayEnabled(context: Context, enabled: Boolean) =
        get(context).edit().putBoolean(KEY_OVERLAY, enabled).apply()

    fun isOverlayEnabled(context: Context): Boolean =
        get(context).getBoolean(KEY_OVERLAY, false)

    fun isFirstLaunch(context: Context): Boolean =
        get(context).getBoolean(KEY_FIRST_LAUNCH, true)

    fun setFirstLaunchDone(context: Context) =
        get(context).edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()

    fun savePicovoiceKey(context: Context, key: String) =
        get(context).edit().putString(KEY_PICOVOICE, key.trim()).apply()

    fun getPicovoiceKey(context: Context): String =
        get(context).getString(KEY_PICOVOICE, "") ?: ""

    fun saveVoiceIntelligenceEnabled(context: Context, enabled: Boolean) =
        get(context).edit().putBoolean(KEY_VOICE_INTEL, enabled).apply()

    fun isVoiceIntelligenceEnabled(context: Context): Boolean =
        get(context).getBoolean(KEY_VOICE_INTEL, false)

    fun saveSelectedModel(context: Context, model: String) =
        get(context).edit().putString(KEY_MODEL, model).apply()

    fun getSelectedModel(context: Context): String? =
        get(context).getString(KEY_MODEL, null)

    fun saveVisionApiKey(context: Context, key: String) =
        get(context).edit().putString(KEY_VISION_API_KEY, key.trim()).apply()

    fun getVisionApiKey(context: Context): String =
        get(context).getString(KEY_VISION_API_KEY, "") ?: ""

    fun saveVisionBaseUrl(context: Context, url: String) =
        get(context).edit().putString(KEY_VISION_BASE_URL, url.trim()).apply()

    fun getVisionBaseUrl(context: Context): String =
        get(context).getString(KEY_VISION_BASE_URL, "") ?: ""

    fun saveVisionModel(context: Context, model: String) =
        get(context).edit().putString(KEY_VISION_MODEL, model).apply()

    fun getVisionModel(context: Context): String =
        get(context).getString(KEY_VISION_MODEL, "gpt-4o") ?: "gpt-4o"

    fun saveVisionProvider(context: Context, provider: String) =
        get(context).edit().putString(KEY_VISION_PROVIDER, provider).apply()

    fun getVisionProvider(context: Context): String =
        get(context).getString(KEY_VISION_PROVIDER, "") ?: ""

    private const val KEY_API_KEY      = "api_key"
    private const val KEY_PROVIDER     = "ai_provider"
    private const val KEY_BIOMETRIC    = "biometric_enabled"
    private const val KEY_TTS          = "tts_enabled"
    private const val KEY_OVERLAY      = "overlay_enabled"
    private const val KEY_FIRST_LAUNCH = "first_launch"
    private const val KEY_PICOVOICE    = "picovoice_key"
    private const val KEY_VOICE_INTEL  = "voice_intel_enabled"
    private const val KEY_MODEL        = "selected_model"
    private const val KEY_BASE_URL     = "base_url"
    private const val KEY_VISION_API_KEY = "vision_api_key"
    private const val KEY_VISION_BASE_URL = "vision_base_url"
    private const val KEY_VISION_MODEL    = "vision_model"
    private const val KEY_VISION_PROVIDER = "vision_provider"
}
