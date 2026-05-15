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

    private val sampleDoc = ExportDocument(
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

    @Test
    fun `Both scope writes both columns`() {
        val out = ByteArrayOutputStream()
        CsvExporter.write(out, sampleDoc, ExportScope.Both)
        val text = out.toString(Charsets.UTF_8)
        assertEquals('﻿', text[0])
        assertTrue(text.contains("Line,Original (Spanish),Translation (English)"))
        assertTrue(text.contains("\"Hola, mundo\""))
        assertTrue(text.contains("Hello, world"))
        assertTrue("CRLF expected between rows", text.contains("\r\n"))
    }

    @Test
    fun `Original scope omits the translation column`() {
        val out = ByteArrayOutputStream()
        CsvExporter.write(out, sampleDoc, ExportScope.Original)
        val text = out.toString(Charsets.UTF_8)
        assertTrue(text.contains("Line,Original (Spanish)"))
        assertTrue(!text.contains("Translation"))
        assertTrue(text.contains("\"Hola, mundo\""))
        assertTrue(!text.contains("Hello, world"))
    }

    @Test
    fun `Translation scope omits the original column`() {
        val out = ByteArrayOutputStream()
        CsvExporter.write(out, sampleDoc, ExportScope.Translation)
        val text = out.toString(Charsets.UTF_8)
        assertTrue(text.contains("Line,Translation (English)"))
        assertTrue(!text.contains("Original"))
        assertTrue(text.contains("Hello, world"))
        assertTrue(!text.contains("Hola"))
    }
}
