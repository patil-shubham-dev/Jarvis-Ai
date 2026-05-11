package com.jarvisai.app.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * HapticUtil: Provides consistent haptic feedback patterns for the Jarvis interface.
 */
object HapticUtil {

    fun vibrate(context: Context, pattern: Pattern) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (pattern) {
                Pattern.LIGHT -> VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
                Pattern.MEDIUM -> VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                Pattern.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 40, 100, 40), intArrayOf(0, 255, 0, 255), -1)
                Pattern.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 50, 100, 50, 100, 50), intArrayOf(0, 255, 0, 255, 0, 255), -1)
                Pattern.PULSE -> VibrationEffect.createOneShot(10, 50)
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern.legacyDuration)
        }
    }

    enum class Pattern(val legacyDuration: Long) {
        LIGHT(20),
        MEDIUM(50),
        SUCCESS(200),
        ERROR(400),
        PULSE(10)
    }
}
