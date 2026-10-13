package com.omnira.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.graphics.toColorInt
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

fun createAttendancePdf(
    context: Context,
    className: String,
    date: String,
    present: List<String>,
    absent: List<String>
): File {
    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas
    val paint = Paint()

    var y = 50f
    paint.textSize = 22f
    paint.isFakeBoldText = true
    canvas.drawText("OMNIRA Attendance Report", 50f, y, paint)
    y += 40f

    paint.textSize = 18f
    canvas.drawText("Class: $className", 50f, y, paint)
    y += 25f
    paint.textSize = 14f
    paint.isFakeBoldText = false
    canvas.drawText("Date: $date", 50f, y, paint)
    y += 40f

    paint.isFakeBoldText = true
    paint.color = "#2E7D32".toColorInt()
    canvas.drawText("Present Students (${present.size}):", 50f, y, paint)
    y += 25f
    paint.isFakeBoldText = false
    paint.color = android.graphics.Color.BLACK
    present.forEach { name ->
        if (y > 800) return@forEach
        canvas.drawText("✓ $name", 70f, y, paint)
        y += 20f
    }

    y += 20f
    if (y < 800) {
        paint.isFakeBoldText = true
        paint.color = "#D32F2F".toColorInt()
        canvas.drawText("Absent Students (${absent.size}):", 50f, y, paint)
        y += 25f
        paint.isFakeBoldText = false
        paint.color = android.graphics.Color.BLACK
        absent.forEach { name ->
            if (y > 800) return@forEach
            canvas.drawText("✗ $name", 70f, y, paint)
            y += 20f
        }
    }

    pdfDocument.finishPage(page)

    val fileName = "Attendance_${className.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
    val file = File(context.cacheDir, fileName)
    pdfDocument.writeTo(FileOutputStream(file))
    pdfDocument.close()
    return file
}

fun saveFileToDownloads(context: Context, file: File, displayName: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val resolver = context.contentResolver
        val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                Toast.makeText(context, "Attendance sheet saved to Downloads", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    } else {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(downloadsDir, displayName)
            file.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(context, "Attendance sheet saved to Downloads", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
