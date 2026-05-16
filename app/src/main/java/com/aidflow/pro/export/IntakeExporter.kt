package com.aidflow.pro.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.aidflow.pro.intake.FamilyRecord
import com.aidflow.pro.intake.IdentifiedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sister to [Exporter] for the structured-intake screens (Family Intake, Items).
 *
 * Excel exports use column names that match the AidFlow Pro web app's canonical
 * Family schema (see web repo `src/services/spreadsheetImport.ts`), so a file
 * dropped into the web app's import dialog requires zero mapping work.
 */
class IntakeExporter(private val context: Context) {

    suspend fun exportFamilies(
        families: List<FamilyRecord>,
        format: ExportFormat,
        baseTitle: String = "aidflow_families",
    ): Uri = exportInternal(format, baseTitle) { out ->
        writeFamilies(out, format, families)
    }

    suspend fun exportItems(
        items: List<IdentifiedItem>,
        format: ExportFormat,
        baseTitle: String = "aidflow_items",
    ): Uri = exportInternal(format, baseTitle) { out ->
        writeItems(out, format, items)
    }

    private suspend fun exportInternal(
        format: ExportFormat,
        baseTitle: String,
        body: (OutputStream) -> Unit,
    ): Uri = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val fileName = "${baseTitle}_$timestamp.${format.extension}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaStoreExport(format, fileName, body)
        } else {
            appFilesExport(format, fileName, body)
        }
    }

    private fun mediaStoreExport(
        format: ExportFormat,
        fileName: String,
        body: (OutputStream) -> Unit,
    ): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, format.mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AidFlow")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore refused to allocate a Downloads entry")
        resolver.openOutputStream(uri)?.use(body)
            ?: error("MediaStore returned a null output stream")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun appFilesExport(
        format: ExportFormat,
        fileName: String,
        body: (OutputStream) -> Unit,
    ): Uri {
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.outputStream().use(body)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    // ----- Family writers -----------------------------------------------

    private fun writeFamilies(out: OutputStream, format: ExportFormat, families: List<FamilyRecord>) {
        when (format) {
            ExportFormat.Xlsx, ExportFormat.Csv -> writeFamiliesTabular(out, format, families)
            ExportFormat.Txt -> writeFamiliesText(out, families)
            ExportFormat.Pdf, ExportFormat.Docx ->
                error("Family intake is exported as XLSX, CSV, or TXT only")
        }
    }

    private fun writeFamiliesTabular(
        out: OutputStream,
        format: ExportFormat,
        families: List<FamilyRecord>,
    ) {
        val rows = families.map { f ->
            listOf(
                f.headName,
                f.memberCount.toString(),
                f.childrenUnder5.toString(),
                f.elderlyCount.toString(),
                if (f.hasPregnantMember) "true" else "false",
                f.medicalConditions,
                f.displacementStatus.webValue,
                f.incomeLevel.webValue,
                f.locationSector,
                f.street,
                f.city,
                f.notes,
                f.priorityScore?.toString().orEmpty(),
                f.priorityReason,
                f.id,
            )
        }
        if (format == ExportFormat.Xlsx) {
            XlsxExporter.writeSingleSheet(out, "Families", FAMILY_HEADERS, rows)
        } else {
            val w = out.bufferedWriter(Charsets.UTF_8)
            w.write("﻿")
            w.append(FAMILY_HEADERS.joinToString(",") { CsvExporter.escape(it) }).append("\r\n")
            rows.forEach { row ->
                w.append(row.joinToString(",") { CsvExporter.escape(it) }).append("\r\n")
            }
            w.flush()
        }
    }

    private fun writeFamiliesText(out: OutputStream, families: List<FamilyRecord>) {
        val w = out.bufferedWriter(Charsets.UTF_8)
        w.appendLine("AidFlow Pro — Family Intake")
        w.appendLine("===========================")
        w.appendLine("Captured: ${families.size}")
        w.appendLine()
        families.forEachIndexed { idx, f ->
            w.appendLine("[${idx + 1}] ${f.headName.ifBlank { "(no name)" }}")
            w.appendLine("    Members: ${f.memberCount} (under-5: ${f.childrenUnder5}, elderly: ${f.elderlyCount})")
            if (f.hasPregnantMember) w.appendLine("    Pregnant member: yes")
            if (f.medicalConditions.isNotBlank()) w.appendLine("    Medical: ${f.medicalConditions}")
            w.appendLine("    Displacement: ${f.displacementStatus.display}")
            w.appendLine("    Income: ${f.incomeLevel.display}")
            val loc = listOf(f.locationSector, f.street, f.city).filter { it.isNotBlank() }.joinToString(", ")
            if (loc.isNotBlank()) w.appendLine("    Location: $loc")
            f.priorityScore?.let {
                w.appendLine("    Priority: $it (${f.priorityLevel.display}) — ${f.priorityReason}")
            }
            if (f.notes.isNotBlank()) w.appendLine("    Notes: ${f.notes}")
            w.appendLine()
        }
        w.flush()
    }

    // ----- Item writers --------------------------------------------------

    private fun writeItems(out: OutputStream, format: ExportFormat, items: List<IdentifiedItem>) {
        val rows = items.mapIndexed { idx, i ->
            listOf(
                (idx + 1).toString(),
                i.name,
                i.quantity?.toString().orEmpty(),
                i.unit,
                i.category.webValue,
                i.notes,
            )
        }
        when (format) {
            ExportFormat.Xlsx ->
                XlsxExporter.writeSingleSheet(out, "Items", ITEM_HEADERS, rows)
            ExportFormat.Csv -> {
                val w = out.bufferedWriter(Charsets.UTF_8)
                w.write("﻿")
                w.append(ITEM_HEADERS.joinToString(",") { CsvExporter.escape(it) }).append("\r\n")
                rows.forEach { row ->
                    w.append(row.joinToString(",") { CsvExporter.escape(it) }).append("\r\n")
                }
                w.flush()
            }
            ExportFormat.Txt -> {
                val w = out.bufferedWriter(Charsets.UTF_8)
                w.appendLine("AidFlow Pro — Item Inventory")
                w.appendLine("============================")
                rows.forEach { r -> w.appendLine(r.joinToString(" | ")) }
                w.flush()
            }
            ExportFormat.Pdf, ExportFormat.Docx ->
                error("Item inventory is exported as XLSX, CSV, or TXT only")
        }
    }

    companion object {
        /** Column order targets the web app's canonical Family schema. */
        val FAMILY_HEADERS = listOf(
            "head_name",
            "member_count",
            "children_under_5",
            "elderly_count",
            "has_pregnant_member",
            "medical_conditions",
            "displacement_status",
            "income_level",
            "location_sector",
            "street",
            "city",
            "notes",
            "priority_score",
            "priority_reason",
            "id",
        )

        val ITEM_HEADERS = listOf("#", "name", "quantity", "unit", "category", "notes")
    }
}
