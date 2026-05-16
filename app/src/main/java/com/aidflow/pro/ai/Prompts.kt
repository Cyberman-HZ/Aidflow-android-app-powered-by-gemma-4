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

    // ---- Structured family intake -------------------------------------------

    private const val FAMILY_SCHEMA = """{
  "head_name": "string",
  "member_count": integer,
  "children_under_5": integer,
  "elderly_count": integer,
  "has_pregnant_member": boolean,
  "medical_conditions": "comma-separated string, empty if none",
  "displacement_status": "one of: resident | recently_displaced | refugee",
  "income_level": "one of: none | minimal | moderate",
  "location_sector": "string",
  "street": "string",
  "city": "string",
  "notes": "string with anything not captured elsewhere",
  "priority_score": integer 0-100,
  "priority_reason": "one sentence justifying the score"
}"""

    private const val FAMILY_RULES =
        "Rules:\n" +
        "- Output ONLY one JSON object, no prose, no markdown fences.\n" +
        "- Use 0 (not null) for missing integer counts.\n" +
        "- Use empty string \"\" for missing text.\n" +
        "- priority_score 80-100 = immediate danger / acute medical / unaccompanied minors,\n" +
        "  60-79 = vulnerable composition (pregnant, infants, elderly, chronic illness)\n" +
        "       + recent displacement + no income,\n" +
        "  40-59 = some vulnerability,\n" +
        "  0-39  = stable family with minor needs.\n" +
        "- Be conservative; if the description is sparse, score lower and say so in priority_reason.\n"

    fun familyFromText(description: String): String = buildString {
        append("You are extracting a humanitarian family record for offline aid coordination. ")
        append("The aid worker has described one family verbally. Map their description into the ")
        append("following JSON schema:\n\n")
        append(FAMILY_SCHEMA)
        append("\n\n")
        append(FAMILY_RULES)
        append("\nDescription:\n")
        append(description.trim())
    }

    fun familyFromImagePrompt(): String = buildString {
        append("You are reading a humanitarian paper registration form photographed by an aid worker. ")
        append("Extract a single family record into this JSON schema:\n\n")
        append(FAMILY_SCHEMA)
        append("\n\n")
        append(FAMILY_RULES)
        append("\nIf a field is illegible, use the empty/zero default rather than guessing.")
    }

    // ---- Item inventory from photo ------------------------------------------

    private const val ITEMS_SCHEMA = """[
  {
    "name": "string (specific noun e.g. 'bottled water 1.5L')",
    "quantity": integer or null,
    "unit": "boxes | bottles | bags | kg | L | packs | units | ''",
    "category": "food | water | medical | shelter | hygiene | clothing | education | other",
    "notes": "anything distinguishing (brand, expiry, condition)"
  }
]"""

    fun identifyItemsPrompt(): String = buildString {
        append("You are an aid-distribution inventory assistant. List every distinct relief item ")
        append("visible in this image. Output ONLY a JSON array matching this schema:\n\n")
        append(ITEMS_SCHEMA)
        append("\n\nRules:\n")
        append("- Estimate quantity only when you can count it clearly. Otherwise use null.\n")
        append("- Group identical items into one entry with the total quantity.\n")
        append("- Omit items you cannot identify with confidence rather than guessing.\n")
        append("- Output ONLY the JSON array. No prose. No markdown fences.")
    }
}
