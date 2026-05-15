package com.aidflow.pro.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LanguagesTest {

    @Test
    fun `every tag is BCP-47 parsable and has matching display`() {
        for (lang in Language.values()) {
            val locale = Locale.forLanguageTag(lang.tag)
            assertNotNull("locale must parse for ${lang.tag}", locale)
            assertEquals(
                "language tag round-trips for ${lang.name}",
                lang.tag.lowercase(),
                locale.toLanguageTag().lowercase(),
            )
            assertTrue("${lang.name} display name blank", lang.displayName.isNotBlank())
            assertTrue("${lang.name} english name blank", lang.englishName.isNotBlank())
        }
    }

    @Test
    fun `fromTag is case-insensitive and accepts known tags`() {
        assertEquals(Language.English, Language.fromTag("EN-US"))
        assertEquals(Language.Arabic, Language.fromTag("ar"))
        assertEquals(null, Language.fromTag("xx-YY"))
        assertEquals(null, Language.fromTag(null))
    }

    @Test
    fun `RTL languages are correctly flagged`() {
        val rtl = setOf(Language.Arabic, Language.Persian, Language.Pashto, Language.Urdu)
        for (lang in Language.values()) {
            assertEquals("${lang.name} RTL flag", lang in rtl, lang.isRtl)
        }
    }
}
