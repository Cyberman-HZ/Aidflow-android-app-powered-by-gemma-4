package com.aidflow.pro.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Top-level coordinator: takes an [ExportDocument] + [ExportFormat], routes to the
 * right serializer, and writes to the Downloads collection via MediaStore (on Q+)
 * or to app-scoped external storage as a fallback. Returns a content:// URI that
 * the caller can hand to [shareIntent].
 */
class Exporter(private val context: Context) {

    suspend fun export(doc: ExportDocument, format: ExportFormat): Uri =
        withContext(Dispatchers.IO) {
            val baseName = sanitize(doc.title)
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val fileName = "${baseName}_$timestamp.${format.extension}"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportViaMediaStore(doc, format, fileName)
            } else {
                exportToAppFiles(doc, format, fileName)
            }
        }

    fun shareIntent(uri: Uri, mime: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun exportViaMediaStore(
        doc: ExportDocument,
        format: ExportFormat,
        fileName: String,
    ): Uri {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, format.mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AidFlow")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: error("MediaStore refused to allocate a Downloads entry")
        resolver.openOutputStream(uri)?.use { writeTo(it, doc, format) }
            ?: error("MediaStore returned a null output stream")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun exportToAppFiles(
        doc: ExportDocument,
        format: ExportFormat,
        fileName: String,
    ): Uri {
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.outputStream().use { writeTo(it, doc, format) }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    private fun writeTo(out: OutputStream, doc: ExportDocument, format: ExportFormat) {
        when (format) {
            ExportFormat.Txt -> TextExporter.write(out, doc)
            ExportFormat.Csv -> CsvExporter.write(out, doc)
            ExportFormat.Pdf -> PdfExporter.write(out, doc)
            ExportFormat.Docx -> DocxExporter.write(out, doc)
        }
    }

    private fun sanitize(title: String): String =
        title.replace(Regex("[^A-Za-z0-9_\\-]+"), "_").trim('_').ifEmpty { "aidflow" }
}
