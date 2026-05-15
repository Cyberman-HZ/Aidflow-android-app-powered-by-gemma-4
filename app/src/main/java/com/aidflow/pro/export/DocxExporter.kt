package com.aidflow.pro.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates a `.docx` (Office Open XML WordprocessingML) by writing exactly the
 * five XML parts that Word needs, zipped together. We deliberately avoid Apache
 * POI — it weighs ~30 MB compressed, vs the ~3 kB this implementation adds.
 *
 * Layout: title (Heading 1), section sub-heading, body paragraphs.
 * Verified against Word 365, LibreOffice Writer, and Google Docs.
 */
object DocxExporter {

    fun write(out: OutputStream, doc: ExportDocument, scope: ExportScope) {
        ZipOutputStream(out).use { zip ->
            zip.entry("[Content_Types].xml", CONTENT_TYPES)
            zip.entry("_rels/.rels", ROOT_RELS)
            zip.entry("word/_rels/document.xml.rels", DOCUMENT_RELS)
            zip.entry("word/styles.xml", STYLES)
            zip.entry("word/document.xml", buildDocument(doc, scope))
        }
    }

    private fun buildDocument(doc: ExportDocument, scope: ExportScope): String {
        val subtitle = when (scope) {
            ExportScope.Original -> doc.sourceLanguage
            ExportScope.Translation -> doc.targetLanguage
            ExportScope.Both -> "From ${doc.sourceLanguage} to ${doc.targetLanguage}"
        }
        val body = buildString {
            append(headingParagraph(doc.title, level = 1))
            append(metaParagraph(subtitle))
            append(emptyParagraph())
            if (scope.includesOriginal) {
                append(headingParagraph("Original", level = 2))
                doc.originalFullText.split('\n').forEach { append(bodyParagraph(it)) }
                append(emptyParagraph())
            }
            if (scope.includesTranslation) {
                append(headingParagraph("Translation", level = 2))
                doc.translatedFullText.split('\n').forEach { append(bodyParagraph(it)) }
            }
        }
        return DOCUMENT_TEMPLATE.replace("{{BODY}}", body)
    }

    private fun headingParagraph(text: String, level: Int): String = """
        <w:p><w:pPr><w:pStyle w:val="Heading${level}"/></w:pPr>
        <w:r><w:t xml:space="preserve">${escape(text)}</w:t></w:r></w:p>
    """.trimIndent()

    private fun bodyParagraph(text: String): String = """
        <w:p><w:r><w:t xml:space="preserve">${escape(text)}</w:t></w:r></w:p>
    """.trimIndent()

    private fun metaParagraph(text: String): String = """
        <w:p><w:pPr><w:pStyle w:val="Subtitle"/></w:pPr>
        <w:r><w:t xml:space="preserve">${escape(text)}</w:t></w:r></w:p>
    """.trimIndent()

    private fun emptyParagraph(): String = "<w:p/>"

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("\r", "")

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private const val DOCUMENT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/><w:sz w:val="22"/></w:rPr></w:rPrDefault>
  </w:docDefaults>
  <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="Heading 1"/>
    <w:pPr><w:spacing w:before="240" w:after="120"/></w:pPr>
    <w:rPr><w:b/><w:sz w:val="36"/></w:rPr></w:style>
  <w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="Heading 2"/>
    <w:pPr><w:spacing w:before="200" w:after="80"/></w:pPr>
    <w:rPr><w:b/><w:sz w:val="28"/></w:rPr></w:style>
  <w:style w:type="paragraph" w:styleId="Subtitle"><w:name w:val="Subtitle"/>
    <w:rPr><w:i/><w:color w:val="555555"/></w:rPr></w:style>
</w:styles>"""

    private const val DOCUMENT_TEMPLATE = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    {{BODY}}
    <w:sectPr>
      <w:pgSz w:w="12240" w:h="15840"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/>
    </w:sectPr>
  </w:body>
</w:document>"""
}
