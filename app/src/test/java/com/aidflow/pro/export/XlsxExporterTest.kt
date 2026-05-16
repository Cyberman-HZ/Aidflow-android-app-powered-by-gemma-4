package com.aidflow.pro.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class XlsxExporterTest {

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val out = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                out[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        return out
    }

    @Test
    fun `single sheet has all required OOXML parts`() {
        val out = ByteArrayOutputStream()
        XlsxExporter.writeSingleSheet(
            out,
            sheetName = "Families",
            header = listOf("head_name", "member_count"),
            rows = listOf(listOf("Ahmed Mahmoud", "5")),
        )
        val parts = unzip(out.toByteArray())
        assertNotNull(parts["[Content_Types].xml"])
        assertNotNull(parts["_rels/.rels"])
        assertNotNull(parts["xl/workbook.xml"])
        assertNotNull(parts["xl/_rels/workbook.xml.rels"])
        assertNotNull(parts["xl/styles.xml"])
        assertNotNull(parts["xl/worksheets/sheet1.xml"])
    }

    @Test
    fun `cells are written as inline strings preserving values`() {
        val out = ByteArrayOutputStream()
        XlsxExporter.writeSingleSheet(
            out,
            sheetName = "Families",
            header = listOf("head_name", "member_count"),
            rows = listOf(listOf("Ahmed Mahmoud", "5"), listOf("Fatima Ali", "3")),
        )
        val sheet = unzip(out.toByteArray()).getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("Ahmed Mahmoud"))
        assertTrue(sheet.contains("Fatima Ali"))
        assertTrue(sheet.contains("""r="A1""""))
        assertTrue(sheet.contains("""r="B2""""))
        // 1 header + 2 data rows = 3 rows
        assertEquals(3, Regex("<row").findAll(sheet).count())
    }

    @Test
    fun `xml-special characters are escaped`() {
        val out = ByteArrayOutputStream()
        XlsxExporter.writeSingleSheet(
            out,
            sheetName = "Items",
            header = listOf("name"),
            rows = listOf(listOf("Tea & biscuits <500g> \"premium\"")),
        )
        val sheet = unzip(out.toByteArray()).getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("Tea &amp; biscuits &lt;500g&gt;"))
        assertTrue(!sheet.contains("Tea & biscuits"))
    }

    @Test
    fun `column reference past Z continues with AA`() {
        // 27 columns means column index 26 maps to AA
        val out = ByteArrayOutputStream()
        val header = (0..26).map { "col$it" }
        XlsxExporter.writeSingleSheet(out, "Wide", header, listOf(header))
        val sheet = unzip(out.toByteArray()).getValue("xl/worksheets/sheet1.xml")
        assertTrue("expected AA1 cell ref", sheet.contains("""r="AA1""""))
    }
}
