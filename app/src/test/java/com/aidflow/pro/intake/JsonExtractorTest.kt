package com.aidflow.pro.intake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class JsonExtractorTest {

    @Test
    fun `firstObject parses a clean JSON object`() {
        val obj = JsonExtractor.firstObject("""{"head_name": "Ahmed", "member_count": 5}""")
        assertNotNull(obj)
        assertEquals("Ahmed", obj!!.getString("head_name"))
        assertEquals(5, obj.getInt("member_count"))
    }

    @Test
    fun `firstObject strips a markdown code fence`() {
        val raw = "```json\n{\"head_name\": \"Fatima\"}\n```"
        val obj = JsonExtractor.firstObject(raw)
        assertEquals("Fatima", obj?.getString("head_name"))
    }

    @Test
    fun `firstObject skips preamble text and finds the first object`() {
        val raw = "Sure! Here is the record:\n{\"head_name\":\"Yusuf\",\"member_count\":3}\nLet me know."
        val obj = JsonExtractor.firstObject(raw)
        assertEquals("Yusuf", obj?.getString("head_name"))
    }

    @Test
    fun `firstObject handles nested braces in strings`() {
        // brace in a string literal should not confuse the balance tracker
        val raw = """Here goes: {"head_name": "weird }{}{ name", "member_count": 1}"""
        val obj = JsonExtractor.firstObject(raw)
        assertEquals("weird }{}{ name", obj?.getString("head_name"))
    }

    @Test
    fun `firstArray parses an item list`() {
        val raw = """[{"name":"Rice 1kg","quantity":12},{"name":"Soap","quantity":24}]"""
        val arr = JsonExtractor.firstArray(raw)
        assertEquals(2, arr?.length())
        assertEquals("Rice 1kg", arr?.getJSONObject(0)?.getString("name"))
    }

    @Test
    fun `firstArray returns null when the model returns garbage`() {
        assertNull(JsonExtractor.firstArray("I cannot help with that."))
    }
}
