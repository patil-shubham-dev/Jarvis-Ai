package com.jarvisai.app.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.jarvisai.app.R
import com.jarvisai.app.ui.activities.MainActivity
import com.jarvisai.app.ui.activities.SettingsActivity
import com.jarvisai.app.utils.SecurePrefs
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Brief splash delay for app branding
        Handler(Looper.getMainLooper()).postDelayed({
            if (!hasApiKeyConfigured()) {
                // First launch or no API key → guide user to settings
                goToSettings()
            } else {
                checkBiometricAndProceed()
            }
        }, 800)
    }

    private fun hasApiKeyConfigured(): Boolean {
        val key = SecurePrefs.getApiKey(this)
        return key.isNotBlank() && key.startsWith("sk-") || key.startsWith("AIza") ||
               key.startsWith("gsk_") || key.startsWith("mi-") ||
               key.startsWith("tog_") || key.startsWith("nvapi-") ||
               key.startsWith("pplx-") || key.length >= 20
    }

    private fun goToSettings() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            putExtra("first_launch", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    private fun checkBiometricAndProceed() {
        if (!SecurePrefs.isBiometricEnabled(this)) {
            goToMain()
            return
        }

        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            showBiometricPrompt()
        } else {
            goToMain()
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    goToMain()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                        goToMain()
                    } else {
                        finish()
                    }
                }
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            })

        biometricPrompt.authenticate(promptInfo)
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
