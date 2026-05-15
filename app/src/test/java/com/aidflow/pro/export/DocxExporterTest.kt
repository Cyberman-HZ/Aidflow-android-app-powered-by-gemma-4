package com.aidflow.pro.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class DocxExporterTest {

    @Test
    fun `produces a valid zip with the expected OOXML parts`() {
        val out = ByteArrayOutputStream()
        val doc = ExportDocument(
            title = "Refugee intake form",
            sourceLanguage = "Arabic",
            targetLanguage = "English",
            lines = listOf(ExportDocument.Line("hello", "marhaba")),
            originalFullText = "Line 1\nLine 2",
            translatedFullText = "Translated line 1\nTranslated line 2",
        )
        DocxExporter.write(out, doc)

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        assertNotNull("[Content_Types].xml", entries["[Content_Types].xml"])
        assertNotNull("_rels/.rels", entries["_rels/.rels"])
        assertNotNull("word/document.xml", entries["word/document.xml"])
        assertNotNull("word/styles.xml", entries["word/styles.xml"])
        assertNotNull("word/_rels/document.xml.rels", entries["word/_rels/document.xml.rels"])

        val document = entries["word/document.xml"]!!
        assertTrue("title body must be present", document.contains("Refugee intake form"))
        assertTrue("subtitle must be present", document.contains("From Arabic to English"))
        assertTrue("original body lines must be present", document.contains("Line 1"))
        assertTrue("translated body lines must be present", document.contains("Translated line 1"))
    }

    @Test
    fun `xml-special characters are escaped`() {
        val out = ByteArrayOutputStream()
        DocxExporter.write(
            out,
            ExportDocument(
                title = "<one> & \"two\"",
                sourceLanguage = "x",
                targetLanguage = "y",
                lines = emptyList(),
                originalFullText = "5 < 6 & 7 > 6",
                translatedFullText = "",
            ),
        )
        var document = ""
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") document = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        assertTrue(document.contains("&lt;one&gt; &amp; &quot;two&quot;"))
        assertTrue(document.contains("5 &lt; 6 &amp; 7 &gt; 6"))
    }
}
