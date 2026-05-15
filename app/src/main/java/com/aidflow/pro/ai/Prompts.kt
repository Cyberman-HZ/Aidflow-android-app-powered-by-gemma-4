package com.aidflow.pro.ai

/**
 * Tuned prompts for the Gemma 4 calls AidFlow Pro makes. Kept in one place so the
 * write-up can cite them and so we can iterate without hunting through call sites.
 */
object Prompts {

    private const val TRANSLATE_SYSTEM =
        "You are a precise, faithful translator. Output ONLY the translation — no preamble, " +
        "no explanation, no quoting. Preserve numbers, proper names, dates, units, and " +
        "formatting (line breaks, bullet markers). If the source contains medical, legal, or " +
        "official terminology, prefer the standard term in the target language. If a passage " +
        "is already in the target language, return it unchanged."

    fun translate(srcLang: String, dstLang: String, text: String): String = buildString {
        append(TRANSLATE_SYSTEM)
        append("\n\n")
        append("Source language: ").append(srcLang).append('\n')
        append("Target language: ").append(dstLang).append("\n\n")
        append("Text:\n")
        append(text)
    }

    private const val OCR_CLEANUP_SYSTEM =
        "The following text was extracted from a photo by OCR. Restore paragraph breaks, " +
        "drop garbled characters, fix obvious OCR confusions (e.g. 'rn'→'m', '0'↔'O' where " +
        "context demands), and reorder fragments if columns were merged. Keep the original " +
        "language. Do not translate. Do not add commentary. Return only the cleaned text."

    fun ocrCleanup(rawText: String, hint: String? = null): String = buildString {
        append(OCR_CLEANUP_SYSTEM)
        if (!hint.isNullOrBlank()) {
            append("\nContext hint: ").append(hint)
        }
        append("\n\nRaw OCR output:\n").append(rawText)
    }

    private const val IMAGE_DESCRIBE_SYSTEM =
        "Read the document in this image. Transcribe every legible text element exactly as " +
        "it appears, preserving line breaks. Do not translate. Do not summarize."

    fun imageDescribe(): String = IMAGE_DESCRIBE_SYSTEM
}
