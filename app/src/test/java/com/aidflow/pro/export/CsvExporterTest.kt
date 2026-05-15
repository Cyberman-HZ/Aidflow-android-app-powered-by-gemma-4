package com.aidflow.pro.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class CsvExporterTest {

    @Test
    fun `escape returns input unchanged when no special chars`() {
        assertEquals("plain text", CsvExporter.escape("plain text"))
    }

    @Test
    fun `escape quotes fields containing comma`() {
        assertEquals("\"a,b\"", CsvExporter.escape("a,b"))
    }

    @Test
    fun `escape doubles internal quotes`() {
        assertEquals("\"she said \"\"hi\"\"\"", CsvExporter.escape("she said \"hi\""))
    }

    @Test
    fun `escape quotes fields containing newline`() {
        assertEquals("\"line1\nline2\"", CsvExporter.escape("line1\nline2"))
    }

    @Test
    fun `write produces UTF-8 BOM, header, then row per line with CRLF`() {
        val doc = ExportDocument(
            title = "t",
            sourceLanguage = "Spanish",
            targetLanguage = "English",
            lines = listOf(
                ExportDocument.Line("Hola, mundo", "Hello, world"),
                ExportDocument.Line("Línea 2", "Line 2"),
            ),
            originalFullText = "x",
            translatedFullText = "y",
        )
        val out = ByteArrayOutputStream()
        CsvExporter.write(out, doc)
        val text = out.toString(Charsets.UTF_8)
        // BOM (U+FEFF) at start
        assertEquals('﻿', text[0])
        // Header fields have spaces and parens but no commas/quotes, so they are not quoted.
        assertTrue("Header row missing", text.contains("Line,Original (Spanish),Translation (English)"))
        assertTrue("CRLF expected between rows", text.contains("\r\n"))
        // Comma-bearing source field must be quoted
        assertTrue(text.contains("\"Hola, mundo\""))
    }
}
