package com.aidflow.pro.translate

import com.aidflow.pro.ai.Gemma4Manager

/**
 * Thin facade over Gemma 4 for the two translation entry points used by the UI:
 *   • text → translation (used by the chat-style translate screen and the OCR pipeline)
 *   • OCR cleanup (kept here so callers don't depend on Gemma4Manager directly)
 *
 * A tiny LRU cache keeps repeated translations of the same string instantaneous —
 * common when a user toggles between original/translated tabs.
 */
class TranslationRepository(private val gemma: Gemma4Manager) {

    private val cache = object : LinkedHashMap<String, String>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > CACHE_SIZE
    }

    suspend fun translate(text: String, from: Language, to: Language): String {
        if (text.isBlank() || from == to) return text
        val key = cacheKey(text, from, to)
        synchronized(cache) { cache[key]?.let { return it } }
        val result = gemma.translate(text, from.englishName, to.englishName)
        synchronized(cache) { cache[key] = result }
        return result
    }

    suspend fun cleanOcr(rawText: String, hint: String? = null): String {
        if (rawText.isBlank()) return rawText
        return gemma.cleanOcr(rawText, hint)
    }

    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    private fun cacheKey(text: String, from: Language, to: Language) =
        "${from.tag}>${to.tag}|${text.hashCode()}|${text.length}"

    companion object { private const val CACHE_SIZE = 64 }
}
