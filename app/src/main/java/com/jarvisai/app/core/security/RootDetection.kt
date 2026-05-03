package com.jarvisai.app.core.security

import android.os.Build

/**
 * Utility object for detecting if the device is rooted.
 * This is a basic implementation that checks for common root indicators.
 */
object RootDetection {
    
    fun isDeviceRooted(): Boolean {
        return checkForSU() || checkForMagisk() || checkForCommonRootPaths()
    }

    private fun checkForSU(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("which su")
            val input = process.inputStream
            val hasOutput = input.available() > 0
            input.close()
            hasOutput
        } catch (e: Exception) {
            false
        }
    }

    private fun checkForMagisk(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("ls /data/adb/")
            val input = process.inputStream
            val hasOutput = input.available() > 0
            input.close()
            hasOutput
        } catch (e: Exception) {
            false
        }
    }

    private fun checkForCommonRootPaths(): Boolean {
        val commonRootPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/system/xbin/su",
            "/system/bin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        
        for (path in commonRootPaths) {
            if (java.io.File(path).exists()) {
                return true
            }
        }
        return false
    }
}
