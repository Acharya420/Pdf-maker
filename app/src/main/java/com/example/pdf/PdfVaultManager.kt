package com.example.pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

class PdfVaultManager(private val context: Context) {

    val pdfsDir: File
        get() = File(context.filesDir, "pdfs").apply { if (!exists()) mkdirs() }

    val exportsDir: File
        get() = File(context.filesDir, "exports").apply { if (!exists()) mkdirs() }

    fun createUniquePdfFile(desiredBaseName: String): File {
        val sanitized = desiredBaseName.trim()
            .removeSuffix(".pdf")
            .replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            .ifBlank { "PDF_Document" }
        var candidate = File(pdfsDir, "$sanitized.pdf")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(pdfsDir, "${sanitized}_$counter.pdf")
            counter++
        }
        return candidate
    }

    fun getShareUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun createSharePdfIntent(file: File): Intent {
        val uri = getShareUri(file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createOpenExternalPdfIntent(file: File): Intent {
        val uri = getShareUri(file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun copyFileToUri(sourceFile: File, destinationUri: Uri): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(destinationUri)?.use { out ->
            sourceFile.inputStream().use { input ->
                input.copyTo(out)
            }
        } ?: error("Unable to open destination output stream.")
    }

    fun resolveDisplayName(uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.lastPathSegment ?: "Document"
        }
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx) ?: "Item"
                }
            }
        return uri.lastPathSegment ?: "Item"
    }

    fun resolveSizeBytes(uri: Uri): Long {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).length() } ?: 0L
        }
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) return cursor.getLong(idx)
                }
            }
        return 0L
    }

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, 3)
            val value = bytes / 1024.0.pow(digitGroups.toDouble())
            return "${DecimalFormat("#,##0.#").format(value)} ${units[digitGroups]}"
        }
    }
}
