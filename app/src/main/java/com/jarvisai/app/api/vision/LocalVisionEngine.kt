package com.jarvisai.app.api.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local Hybrid Vision Engine for Sentinel V4.1.
 * Optimized for MobileNet V3 (via ML Kit & Custom TFLite fallback).
 */
@Singleton
class LocalVisionEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    // Primary Labeler (General Purpose)
    private val generalLabeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.35f)
            .build()
    )

    // Optional: Custom MobileNet V3 Labeler
    private var customLabeler: com.google.mlkit.vision.label.ImageLabeler? = null

    init {
        setupCustomModel()
    }

    private fun setupCustomModel() {
        try {
            // Check if user has provided a custom MobileNet V3 model in assets
            val modelPath = "mobilenet_v3.tflite"
            val assets = context.assets.list("") ?: emptyArray()
            
            if (assets.contains(modelPath)) {
                Log.i(TAG, "Found custom MobileNet V3 model. Initializing custom labeler...")
                val localModel = LocalModel.Builder()
                    .setAssetFilePath(modelPath)
                    .build()
                val customOptions = CustomImageLabelerOptions.Builder(localModel)
                    .setConfidenceThreshold(0.4f)
                    .setMaxResultCount(5)
                    .build()
                customLabeler = ImageLabeling.getClient(customOptions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup custom vision model", e)
        }
    }

    companion object {
        private const val TAG = "LocalVisionEngine"
    }

    suspend fun analyze(bitmap: Bitmap): String {
        Log.d(TAG, "Starting screen analysis...")
        
        val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        
        val image = InputImage.fromBitmap(softwareBitmap, 0)

        return try {
            val analysisResult = withTimeoutOrNull(8000) { 
                Log.d(TAG, "Starting OCR task...")
                val textTask = textRecognizer.process(image)
                Log.d(TAG, "Starting Labeling task...")
                val labelTask = (customLabeler ?: generalLabeler).process(image)
                
                Log.d(TAG, "Awaiting ML Kit results (8s timeout)...")
                val textResult = textTask.await()
                Log.d(TAG, "OCR completed.")
                val labelResult = labelTask.await()
                Log.d(TAG, "Labeling completed.")
                
                Pair(textResult, labelResult)
            }

            if (analysisResult == null) {
                Log.w(TAG, "Analysis timed out")
                return "Vision analysis timed out. Screen content might be complex."
            }

            val (textResult, labelResult) = analysisResult
            
            buildString {
                append("VISION CONTEXT (${if (customLabeler != null) "MobileNet V3" else "Standard ML Kit"}):\n")
                
                if (labelResult.isNotEmpty()) {
                    append("Detected: ")
                    append(labelResult.take(5).joinToString { label: ImageLabel -> 
                        "${label.text} (${(label.confidence * 100).toInt()}%)" 
                    })
                    append("\n\n")
                }

                if (textResult.text.isNotBlank()) {
                    append("OCR Output:\n")
                    append(textResult.text.replace("\n\n", "\n").take(800))
                } else {
                    append("No readable text found on screen.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vision Analysis Error", e)
            "Vision analysis failed: ${e.localizedMessage}. Falling back to accessibility tree."
        }
 finally {
            if (softwareBitmap != bitmap) {
                softwareBitmap.recycle()
            }
        }
    }

    /**
     * Finds the center coordinates of a specific query (text or label) on the screen.
     * Tries OCR first, then falls back to labeling if custom model is available.
     */
    suspend fun findTextCoordinates(bitmap: Bitmap, query: String): android.graphics.Point? {
        val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
        val image = InputImage.fromBitmap(softwareBitmap, 0)

        return try {
            // 1. Precise OCR Search
            val textResult = textRecognizer.process(image).await()
            for (block in textResult.textBlocks) {
                for (line in block.lines) {
                    if (line.text.contains(query, ignoreCase = true)) {
                        val rect = line.boundingBox ?: continue
                        return android.graphics.Point(rect.centerX(), rect.centerY())
                    }
                }
            }

            // 2. Visual Label Search (Fallback)
            if (customLabeler != null) {
                val labels = customLabeler?.process(image)?.await() ?: emptyList()
                val bestMatch = labels.find { it.text.contains(query, ignoreCase = true) && it.confidence > 0.5f }
                if (bestMatch != null) {
                    Log.d(TAG, "Found visual match via labeler: ${bestMatch.text}")
                    // Note: Labels usually don't have coordinates in standard ML Kit labeling
                    // If we need coordinates for icons, we'd use Object Detection.
                }
            }
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Coordinate search failed", e)
            null
        } finally {
            if (softwareBitmap != bitmap) softwareBitmap.recycle()
        }
    }

    suspend fun findObjectCoordinates(bitmap: Bitmap, query: String): android.graphics.Point? = findTextCoordinates(bitmap, query)
}
