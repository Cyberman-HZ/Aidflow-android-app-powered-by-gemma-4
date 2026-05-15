package com.aidflow.pro.export

import java.io.OutputStream

/**
 * Minimal RFC 4180-conformant CSV writer. Quotes any cell containing commas,
 * quotes, CR, or LF, and doubles internal quote characters.
 *
 * Column layout depends on [ExportScope]:
 *   • Both        → Line, Original (src), Translation (dst)
 *   • Original    → Line, Original (src)
 *   • Translation → Line, Translation (dst)
 */
object CsvExporter {

    private const val BOM = "﻿"

    fun write(out: OutputStream, doc: ExportDocument, scope: ExportScope) {
        val writer = out.bufferedWriter(Charsets.UTF_8)
        // Excel sniffs the BOM to detect UTF-8 reliably
        writer.write(BOM)

        val header = buildList {
            add("Line")
            if (scope.includesOriginal) add("Original (${doc.sourceLanguage})")
            if (scope.includesTranslation) add("Translation (${doc.targetLanguage})")
        }
        writer.appendRow(*header.toTypedArray())

        doc.lines.forEachIndexed { index, line ->
            val row = buildList {
                add((index + 1).toString())
                if (scope.includesOriginal) add(line.original)
                if (scope.includesTranslation) add(line.translated)
            }
            writer.appendRow(*row.toTypedArray())
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
