package com.aidflow.pro.intake

import org.json.JSONArray
import org.json.JSONObject

/**
 * Defensive parser for JSON produced by an LLM. Even with strict instructions,
 * Gemma occasionally wraps output in ```json fences, prepends an "Here is the
 * extracted record:" preamble, or appends a trailing comment. This extractor
 * strips fences and scans for the first balanced JSON object / array.
 */
object JsonExtractor {

    fun firstObject(raw: String): JSONObject? = firstBlock(raw, '{', '}')?.let {
        runCatching { JSONObject(it) }.getOrNull()
    }

    fun firstArray(raw: String): JSONArray? = firstBlock(raw, '[', ']')?.let {
        runCatching { JSONArray(it) }.getOrNull()
    }

    /** Returns the substring of the first balanced bracket-pair, or null. */
    private fun firstBlock(raw: String, open: Char, close: Char): String? {
        val cleaned = stripFences(raw)
        val start = cleaned.indexOf(open)
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until cleaned.length) {
            val c = cleaned[i]
            if (escape) { escape = false; continue }
            if (c == '\\') { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            if (c == open) depth++
            else if (c == close) {
                depth--
                if (depth == 0) return cleaned.substring(start, i + 1)
            }
        }
        return null
    }

    private fun stripFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val firstNewline = trimmed.indexOf('\n')
        if (firstNewline < 0) return trimmed
        val withoutOpen = trimmed.substring(firstNewline + 1)
        val closeFence = withoutOpen.lastIndexOf("```")
        return if (closeFence > 0) withoutOpen.substring(0, closeFence).trim() else withoutOpen
    }

    // ---- typed helpers --------------------------------------------------

    fun JSONObject.optInteger(key: String): Int? =
        if (isNull(key)) null else optString(key, "").toIntOrNull() ?: optInt(key, Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE }

    fun JSONObject.optBoolean2(key: String, default: Boolean): Boolean =
        when {
            isNull(key) -> default
            has(key) -> {
                val v = get(key)
                when (v) {
                    is Boolean -> v
                    is String -> v.equals("true", true) || v.equals("yes", true) || v == "1"
                    is Number -> v.toInt() != 0
                    else -> default
                }
            }
            else -> default
        }

    fun JSONObject.optStr(key: String, default: String = ""): String =
        if (isNull(key) || !has(key)) default
        else when (val v = get(key)) {
            is String -> v
            is JSONArray -> (0 until v.length()).joinToString(", ") { v.optString(it) }
            else -> v.toString()
        }
}
