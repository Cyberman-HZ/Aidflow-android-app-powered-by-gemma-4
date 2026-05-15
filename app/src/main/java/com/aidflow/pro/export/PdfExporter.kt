package com.aidflow.pro.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/**
 * Hand-rolled PDF generation via [android.graphics.pdf.PdfDocument].
 * One-column A4 layout, fixed margins, simple wrap-by-word.
 *
 * No third-party dependency — keeps the APK small and avoids licensing
 * complications. Sufficient for the linear OCR-then-translate output AidFlow
 * Pro produces. (More elaborate typesetting is a deliberate non-goal.)
 */
object PdfExporter {

    private const val PAGE_WIDTH = 595   // A4 width in points (8.27 in × 72)
    private const val PAGE_HEIGHT = 842  // A4 height in points (11.69 in × 72)
    private const val MARGIN = 48f
    private const val LINE_HEIGHT_BODY = 16f
    private const val LINE_HEIGHT_HEADING = 22f

    fun write(out: OutputStream, doc: ExportDocument, scope: ExportScope) {
        val pdf = PdfDocument()

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val sectionPaint = Paint(titlePaint).apply { textSize = 13f }
        val bodyPaint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val mutedPaint = Paint(bodyPaint).apply { color = Color.DKGRAY; textSize = 10f }

        val writer = PageWriter(pdf, bodyPaint)

        writer.drawText(doc.title, titlePaint, LINE_HEIGHT_HEADING)
        val subtitle = when (scope) {
            ExportScope.Original -> doc.sourceLanguage
            ExportScope.Translation -> doc.targetLanguage
            ExportScope.Both -> "From ${doc.sourceLanguage} to ${doc.targetLanguage}"
        }
        writer.drawText(subtitle, mutedPaint, LINE_HEIGHT_BODY)
        writer.skip(LINE_HEIGHT_BODY)

        if (scope.includesOriginal) {
            writer.drawText("Original", sectionPaint, LINE_HEIGHT_HEADING)
            writeParagraphs(writer, doc.originalFullText, bodyPaint)
            writer.skip(LINE_HEIGHT_BODY)
        }
        if (scope.includesTranslation) {
            writer.drawText("Translation", sectionPaint, LINE_HEIGHT_HEADING)
            writeParagraphs(writer, doc.translatedFullText, bodyPaint)
        }

        writer.finish()
        pdf.writeTo(out)
        pdf.close()
    }

    private fun writeParagraphs(writer: PageWriter, body: String, paint: Paint) {
        for (paragraph in body.split("\n")) {
            if (paragraph.isBlank()) {
                writer.skip(LINE_HEIGHT_BODY / 2)
                continue
            }
            for (wrapped in wrap(paragraph, paint, PAGE_WIDTH - 2 * MARGIN)) {
                writer.drawText(wrapped, paint, LINE_HEIGHT_BODY)
            }
        }
    }

    private fun wrap(line: String, paint: Paint, maxWidth: Float): List<String> {
        if (paint.measureText(line) <= maxWidth) return listOf(line)
        val words = line.split(" ")
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current.clear(); current.append(candidate)
            } else {
                if (current.isNotEmpty()) out += current.toString()
                if (paint.measureText(word) <= maxWidth) {
                    current.clear(); current.append(word)
                } else {
                    // Word is itself wider than the line — hard-break by chars.
                    var i = 0
                    while (i < word.length) {
                        var end = word.length
                        while (end > i && paint.measureText(word.substring(i, end)) > maxWidth) end--
                        if (end == i) end = i + 1
                        out += word.substring(i, end)
                        i = end
                    }
                    current.clear()
                }
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    private class PageWriter(
        private val pdf: PdfDocument,
        private val basePaint: Paint,
    ) {
        private var pageIndex = 0
        private var page: PdfDocument.Page? = null
        private var y: Float = 0f

        init { newPage() }

        fun drawText(text: String, paint: Paint, lineHeight: Float) {
            ensureRoom(lineHeight)
            page!!.canvas.drawText(text, MARGIN, y, paint)
            y += lineHeight
        }

        fun skip(height: Float) {
            ensureRoom(height)
            y += height
        }

        fun finish() {
            page?.let { pdf.finishPage(it) }
            page = null
        }

        private fun ensureRoom(lineHeight: Float) {
            if (y + lineHeight > PAGE_HEIGHT - MARGIN) {
                finish()
                newPage()
            }
        }

        private fun newPage() {
            pageIndex += 1
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex).create()
            page = pdf.startPage(info)
            y = MARGIN + basePaint.textSize
        }
    }
}
