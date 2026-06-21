package com.jarvisai.app.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.jarvisai.app.notifications.JarvisNotificationManager

/**
 * Service to handle real-time screen capture via MediaProjection API.
 */
class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", 0) ?: 0
        val resultData = intent?.getParcelableExtra<Intent>("RESULT_DATA")

        if (resultCode != 0 && resultData != null) {
            startProjection(resultCode, resultData)
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun startProjection(resultCode: Int, resultData: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, resultData)

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "JarvisScreenCapture",
            metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        synchronized(ScreenCaptureService::class.java) { instance = this }
        Log.d("ScreenCapture", "Projection started successfully")
    }

    fun captureScreenshot(): Bitmap? {
        val image = imageReader?.acquireLatestImage() ?: return null
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride

        val bitmapWidth = image.width
        val bitmapHeight = image.height
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)

        if (rowStride == bitmapWidth * 4) {
            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)
        } else {
            val pixels = IntArray(bitmapWidth * bitmapHeight)
            val rowBytes = ByteArray(rowStride)
            for (y in 0 until bitmapHeight) {
                buffer.position(y * rowStride)
                buffer.get(rowBytes)
                for (x in 0 until bitmapWidth) {
                    val idx = x * pixelStride
                    val a = rowBytes[idx + 3].toInt() and 0xFF
                    val r = rowBytes[idx].toInt() and 0xFF
                    val g = rowBytes[idx + 1].toInt() and 0xFF
                    val b = rowBytes[idx + 2].toInt() and 0xFF
                    pixels[y * bitmapWidth + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            bitmap.setPixels(pixels, 0, bitmapWidth, 0, 0, bitmapWidth, bitmapHeight)
        }

        image.close()
        return bitmap
    }

    private fun buildNotification(): Notification {
        JarvisNotificationManager.ensureChannels(this)
        return JarvisNotificationManager.buildServiceNotification(
            this, "Jarvis Eyes Active", "Monitoring screen for autonomous assistance."
        )
    }

    override fun onDestroy() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        instance = null
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 2002
        @Volatile
        var instance: ScreenCaptureService? = null
    }
}
