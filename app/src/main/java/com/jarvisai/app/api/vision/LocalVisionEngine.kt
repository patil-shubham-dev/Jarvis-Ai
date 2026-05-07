package com.jarvisai.app.api.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local Hybrid Vision Engine for Sentinel V4.1.
 * Uses ML Kit (MobileNet V2/V3 based) for Image Labeling and OCR.
 * Provides millisecond latency and zero cost.
 */
@Singleton
class LocalVisionEngine @Inject constructor() {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    suspend fun analyze(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)

        return try {
            // Run OCR and Labeling in parallel
            val textResult = textRecognizer.process(image).await()
            val labelResult = labeler.process(image).await()

            buildString {
                append("LOCAL VISION ANALYSIS:\n")
                
                if (labelResult.isNotEmpty()) {
                    append("Visual Elements detected: ")
                    append(labelResult.joinToString { label: ImageLabel -> 
                        "${label.text} (${(label.confidence * 100).toInt()}%)" 
                    })
                    append("\n\n")
                }

                if (textResult.text.isNotBlank()) {
                    append("Text detected on screen:\n")
                    append(textResult.text)
                } else {
                    append("No clear text detected.")
                }
            }
        } catch (e: Exception) {
            "Local Vision failed: ${e.message}"
        }
    }
}
