package com.aidflow.pro.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Hand-rolled minimal `.xlsx` (Office Open XML SpreadsheetML) writer.
 *
 * The user-facing payload is the list of rows in [Sheet.rows]. Cells are written
 * as inline strings (`t="inlineStr"`) which keeps the file self-contained
 * without a sharedStrings part; for the small fact-sheets AidFlow Pro produces
 * the size cost is negligible vs. the simplicity gain.
 *
 * Verified to open in Microsoft Excel 365, LibreOffice Calc, Google Sheets,
 * and Apple Numbers.
 */
object XlsxExporter {

    data class Sheet(
        val name: String,
        val rows: List<List<String>>,
    )

    fun write(out: OutputStream, sheets: List<Sheet>) {
        require(sheets.isNotEmpty()) { "At least one sheet required" }
        ZipOutputStream(out).use { zip ->
            zip.entry("[Content_Types].xml", contentTypes(sheets.size))
            zip.entry("_rels/.rels", ROOT_RELS)
            zip.entry("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            zip.entry("xl/workbook.xml", workbook(sheets))
            zip.entry("xl/styles.xml", STYLES)
            sheets.forEachIndexed { idx, sheet ->
                zip.entry("xl/worksheets/sheet${idx + 1}.xml", sheetXml(sheet))
            }
        }
    }

    /** Convenience: write a single sheet with a header row + data rows. */
    fun writeSingleSheet(
        out: OutputStream,
        sheetName: String,
        header: List<String>,
        rows: List<List<String>>,
    ) = write(out, listOf(Sheet(sheetName, listOf(header) + rows)))

    private fun sheetXml(sheet: Sheet): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""").append('\n')
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("<sheetData>")
        sheet.rows.forEachIndexed { rowIdx, row ->
            sb.append("<row r=\"").append(rowIdx + 1).append("\">")
            row.forEachIndexed { colIdx, value ->
                val cellRef = cellRef(colIdx, rowIdx)
                sb.append("<c r=\"").append(cellRef).append("\" t=\"inlineStr\">")
                sb.append("<is><t xml:space=\"preserve\">")
                sb.append(escape(value))
                sb.append("</t></is></c>")
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun workbook(sheets: List<Sheet>): String {
        val refs = sheets.mapIndexed { idx, s ->
            """<sheet name="${escapeAttr(s.name)}" sheetId="${idx + 1}" r:id="rId${idx + 1}"/>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>$refs</sheets>
</workbook>"""
    }

    private fun workbookRels(sheetCount: Int): String {
        val rels = (1..sheetCount).joinToString("") { idx ->
            """<Relationship Id="rId$idx" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$idx.xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  $rels
  <Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
    }

    private fun contentTypes(sheetCount: Int): String {
        val sheets = (1..sheetCount).joinToString("") { idx ->
            """<Override PartName="/xl/worksheets/sheet$idx.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  $sheets
</Types>"""
    }

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
  <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
  <borders count="1"><border/></borders>
  <cellStyleXfs count="1"><xf/></cellStyleXfs>
  <cellXfs count="1"><xf/></cellXfs>
</styleSheet>"""

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun cellRef(col: Int, row: Int): String {
        // Excel columns: A..Z, AA..AZ, ...
        var c = col
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + (c % 26)))
            c = c / 26 - 1
        } while (c >= 0)
        sb.append(row + 1)
        return sb.toString()
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\r", "")

    private fun escapeAttr(s: String): String = escape(s)
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
