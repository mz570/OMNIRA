package com.omnira.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.omnira.BuildConfig
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "StorageUtils"

fun uploadToCloudinary(context: Context, uri: Uri, folder: String, onResult: (String?) -> Unit) {
    val uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET
    if (uploadPreset.isBlank()) {
        Toast.makeText(context, "Media uploads are not configured. Set cloudinaryUploadPreset in your local Gradle properties.", Toast.LENGTH_LONG).show()
        onResult(null)
        return
    }

    try {
        Log.d(TAG, "Starting upload to folder: $folder")
        MediaManager.get().upload(uri)
            .option("folder", folder)
            .option("upload_preset", uploadPreset)
            .option("resource_type", "auto")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {
                    Log.d(TAG, "Upload started: $requestId")
                }
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                    val progress = (bytes.toDouble() / totalBytes * 100).toInt()
                    Log.d(TAG, "Upload progress: $progress%")
                }
                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val url = resultData["secure_url"] as? String
                    Log.d(TAG, "Upload success! URL: $url")
                    onResult(url)
                }
                override fun onError(requestId: String, error: ErrorInfo) {
                    Log.e(TAG, "Upload error ($requestId): ${error.description} (Code: ${error.code})")
                    onResult(null)
                }
                override fun onReschedule(requestId: String, error: ErrorInfo) {
                    Log.w(TAG, "Upload rescheduled: ${error.description}")
                }
            }).dispatch()
    } catch (e: Exception) {
        Log.e(TAG, "Exception during upload: ${e.message}", e)
        onResult(null)
    }
}

/**
 * Downloads a file using the System DownloadManager.
 * Sanitizes the filename to prevent issues with special characters or slashes.
 */
fun downloadFile(context: Context, url: String, fileName: String?) {
    if (url.isBlank()) {
        Toast.makeText(context, "Invalid download link", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        // Clean URL to handle potential Cloudinary transformations or query params
        val cleanUrl = url.substringBefore("?")
        
        // Extract extension more reliably
        val extension = MimeTypeMap.getFileExtensionFromUrl(cleanUrl).lowercase()
        
        // Sanitize the filename: remove illegal characters
        var sanitizedName = fileName?.replace(Regex("[^a-zA-Z0-9.-]"), "_") ?: "OMNIRA_File_${System.currentTimeMillis()}"
        
        // Ensure it doesn't have internal path segments
        sanitizedName = sanitizedName.substringAfterLast("/")

        // Ensure it has an extension if we can determine it
        if (!sanitizedName.contains(".") && extension.isNotBlank()) {
            sanitizedName = "$sanitizedName.$extension"
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(sanitizedName)
            .setDescription("Downloading from OMNIRA")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, sanitizedName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(context, "Download started: $sanitizedName", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Log.e(TAG, "Download failed: ${e.message}")
        Toast.makeText(context, "Download failed. Please check internet connection.", Toast.LENGTH_LONG).show()
    }
}

fun getFileName(context: Context, uri: Uri): String {
    var name = "file"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) {
                val fileName = cursor.getString(nameIndex)
                if (fileName != null) name = fileName
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error getting file name", e)
    }
    return name
}

fun isImage(fileName: String?): Boolean {
    val name = fileName?.lowercase() ?: return false
    return name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg") || 
           name.endsWith(".webp") || name.endsWith(".gif")
}

fun formatDate(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(timestamp))
}

fun isDateExpired(dateStr: String): Boolean {
    if (dateStr.isEmpty()) return false
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
    return try {
        val date = sdf.parse(dateStr)
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        date != null && date.before(calendar.time)
    } catch (e: Exception) {
        false
    }
}

fun isDateTimeExpired(dateStr: String, timeStr: String): Boolean {
    if (dateStr.isEmpty()) return false
    val dateTimeStr = if (timeStr.isEmpty()) dateStr else "$dateStr $timeStr"
    val pattern = if (timeStr.isEmpty()) "dd/MM/yyyy" else "dd/MM/yyyy HH:mm"
    val sdf = SimpleDateFormat(pattern, Locale.ENGLISH)
    return try {
        val date = sdf.parse(dateTimeStr)
        date != null && date.before(Date())
    } catch (e: Exception) {
        false
    }
}

fun getDayOfWeekFromDate(dateStr: String): String {
    if (dateStr.isEmpty()) return ""
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
    return try {
        val date = sdf.parse(dateStr)
        SimpleDateFormat("EEEE", Locale.ENGLISH).format(date!!)
    } catch (e: Exception) {
        ""
    }
}
