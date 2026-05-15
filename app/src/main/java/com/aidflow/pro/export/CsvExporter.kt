package com.aidflow.pro.export

import java.io.OutputStream

/**
 * Minimal RFC 4180-conformant CSV writer. Quotes any cell containing commas,
 * quotes, CR, or LF, and doubles internal quote characters.
 */
object CsvExporter {

    fun write(out: OutputStream, doc: ExportDocument) {
        val writer = out.bufferedWriter(Charsets.UTF_8)
        // Excel sniffs the BOM to detect UTF-8 reliably
        writer.write("﻿")
        writer.appendRow("Line", "Original (${doc.sourceLanguage})", "Translation (${doc.targetLanguage})")
        doc.lines.forEachIndexed { index, line ->
            writer.appendRow((index + 1).toString(), line.original, line.translated)
        }
        writer.flush()
    }

    fun escape(field: String): String {
        val needsQuoting = field.contains(',') ||
            field.contains('"') ||
            field.contains('\n') ||
            field.contains('\r')
        return if (needsQuoting) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else field
    }

    private fun Appendable.appendRow(vararg fields: String) {
        append(fields.joinToString(",") { escape(it) })
        append("\r\n")
    }
}
