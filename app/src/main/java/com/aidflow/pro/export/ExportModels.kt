package com.aidflow.pro.export

/** What kind of file an exporter produces. */
enum class ExportFormat(val mime: String, val extension: String) {
    Txt("text/plain", "txt"),
    Csv("text/csv", "csv"),
    Pdf("application/pdf", "pdf"),
    Docx("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
}

/**
 * The payload an exporter consumes. Carries enough information that a CSV row,
 * a PDF page, and a DOCX paragraph can all be generated from the same data.
 *
 * For the "scan a document and translate it" flow, lines come from the OCR output.
 * For the "voice translate" flow, lines come from the transcribed sentences.
 */
data class ExportDocument(
    val title: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val lines: List<Line>,
    val originalFullText: String,
    val translatedFullText: String,
) {
    data class Line(val original: String, val translated: String)

    companion object {
        fun fromOcrPipeline(
            title: String,
            sourceLang: String,
            targetLang: String,
            originalLines: List<String>,
            translatedFullText: String,
        ): ExportDocument {
            val translatedLines = translatedFullText.lines().filter { it.isNotBlank() }
            val pairs = originalLines.mapIndexed { i, src ->
                Line(src, translatedLines.getOrNull(i).orEmpty())
            }
            return ExportDocument(
                title = title,
                sourceLanguage = sourceLang,
                targetLanguage = targetLang,
                lines = pairs,
                originalFullText = originalLines.joinToString("\n"),
                translatedFullText = translatedFullText,
            )
        }
    }
}
