package com.aidflow.pro.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {

    @Test
    fun `translate prompt names source and target languages and embeds text verbatim`() {
        val prompt = Prompts.translate("Spanish", "English", "Hola mundo")
        assertTrue("must include source lang", prompt.contains("Source language: Spanish"))
        assertTrue("must include target lang", prompt.contains("Target language: English"))
        assertTrue("must embed the text", prompt.contains("Hola mundo"))
        assertTrue("must demand translation-only output", prompt.contains("ONLY the translation"))
    }

    @Test
    fun `ocrCleanup prompt forbids translation and accepts optional context hint`() {
        val noHint = Prompts.ocrCleanup("garbled\n\noutput")
        assertTrue(noHint.contains("Do not translate"))
        assertTrue(noHint.contains("garbled"))
        val withHint = Prompts.ocrCleanup("scribbles", hint = "Spanish prescription")
        assertTrue(withHint.contains("Context hint: Spanish prescription"))
    }
}
