package com.jarvisai.app.core.visual

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * PrivacyProtector: Identifies and masks sensitive information on the screen.
 * Uses pattern matching and ML Kit Text Recognition to protect user privacy.
 */
object PrivacyProtector {
    private const val TAG = "PrivacyProtector"
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Regex for common sensitive data
    private val SENSITIVE_PATTERNS = listOf(
        Regex("""\b\d{4}[ -]?\d{4}[ -]?\d{4}[ -]?\d{4}\b"""), // Credit Card
        Regex("""\b[A-Z]{2}\d{2}[A-Z0-9]{11,30}\b"""), // IBAN
        Regex("""\b\d{3}-\d{2}-\d{4}\b"""), // SSN
        Regex("""\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}\b""") // Email (optional, but good for privacy)
    )

    private val SENSITIVE_LABELS = listOf("password", "cvv", "pin", "code", "ssn", "secret")

    suspend fun maskSensitiveData(bitmap: Bitmap): Bitmap {
        val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            bitmap.copy(bitmap.config, true)
        }

        val canvas = Canvas(softwareBitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        try {
            val image = InputImage.fromBitmap(softwareBitmap, 0)
            val result = recognizer.process(image).await()

            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val text = line.text.lowercase()
                    val rect = line.boundingBox ?: continue

                    // 1. Check for Label-based sensitivity (e.g. "Password: ****")
                    val isLabel = SENSITIVE_LABELS.any { label -> text.contains(label) }
                    
                    // 2. Check for Pattern-based sensitivity (e.g. CC numbers)
                    val isPatternMatch = SENSITIVE_PATTERNS.any { it.containsMatchIn(line.text) }

                    if (isLabel || isPatternMatch) {
                        Log.i(TAG, "Masking sensitive area: $text")
                        // Mask the area. If it's a label, we might want to mask the area to the right/bottom of it.
                        // For now, we mask the line itself and a heuristic area next to it if it's a label.
                        canvas.drawRect(rect, paint)
                        
                        if (isLabel && text.length < 15) {
                            // Mask adjacent area (common for "Password: [input]")
                            val extraRect = Rect(rect.right, rect.top, rect.right + 400, rect.bottom)
                            canvas.drawRect(extraRect, paint)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Privacy masking failed", e)
        }

        return softwareBitmap
    }
}
