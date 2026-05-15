package com.aidflow.pro.export

import java.io.OutputStream

object TextExporter {
    fun write(out: OutputStream, doc: ExportDocument, scope: ExportScope) {
        val writer = out.bufferedWriter(Charsets.UTF_8)
        writer.appendLine(doc.title)
        writer.appendLine("=".repeat(doc.title.length.coerceAtLeast(8)))
        writer.appendLine()
        if (scope.includesOriginal) {
            writer.appendLine("Source language: ${doc.sourceLanguage}")
        }
        if (scope.includesTranslation) {
            writer.appendLine("Target language: ${doc.targetLanguage}")
        }
        writer.appendLine()
        if (scope.includesOriginal) {
            writer.appendLine("--- Original ---")
            writer.appendLine(doc.originalFullText)
            writer.appendLine()
        }
        if (scope.includesTranslation) {
            writer.appendLine("--- Translation ---")
            writer.appendLine(doc.translatedFullText)
        }
        writer.flush()
    }
}
