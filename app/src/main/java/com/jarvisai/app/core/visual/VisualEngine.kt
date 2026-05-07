package com.jarvisai.app.core.visual

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jarvisai.app.core.util.await
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisualEngine @Inject constructor() {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    data class TextBlock(val text: String, val center: Point)

    suspend fun analyzeScreen(bitmap: Bitmap): List<TextBlock> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            
            val blocks = mutableListOf<TextBlock>()
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    val rect: android.graphics.Rect = line.boundingBox ?: continue
                    val centerX = (rect.left + rect.right) / 2
                    val centerY = (rect.top + rect.bottom) / 2
                    blocks.add(TextBlock(line.text, Point(centerX, centerY)))
                }
            }
            blocks
        } catch (e: Exception) {
            Log.e("VisualEngine", "OCR processing failed", e)
            emptyList()
        }
    }

    suspend fun findTextCoordinates(bitmap: Bitmap, query: String): Point? {
        val blocks = analyzeScreen(bitmap)
        return blocks.find { it.text.contains(query, ignoreCase = true) }?.center
    }
}
