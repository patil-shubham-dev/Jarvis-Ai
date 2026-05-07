package com.jarvisai.app.core.action.agents

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.jarvisai.app.service.JarvisOverlayService
import java.io.File

object FileAgent {
    private const val TAG = "FileAgent"

    fun findLastDownloadedFile(context: Context, extension: String = ".pdf"): File? {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val files = downloadDir.listFiles { f: File -> 
            f.isFile && f.name.endsWith(extension, ignoreCase = true) 
        }
        return files?.maxByOrNull { f: File -> f.lastModified() }
    }

    fun shareFile(context: Context, file: File, packageName: String? = null): Boolean {
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (packageName != null) {
                    setPackage(packageName)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share file", e)
            false
        }
    }
}
