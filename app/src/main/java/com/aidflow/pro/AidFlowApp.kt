package com.aidflow.pro

import android.app.Application
import com.aidflow.pro.ai.Gemma4Manager
import com.aidflow.pro.ai.ModelDownloader
import com.aidflow.pro.ocr.OcrEngine
import com.aidflow.pro.translate.TranslationRepository

/**
 * Application owns the singletons whose lifecycle should match the process:
 *   • Gemma 4 engine + conversation (expensive to recreate)
 *   • ML Kit recognizer (holds native handles)
 *
 * We construct lazily so the cold-start path remains fast — the user sees the
 * Activity before we pay any of these costs.
 */
class AidFlowApp : Application() {

    val gemma: Gemma4Manager by lazy { Gemma4Manager(this) }
    val modelDownloader: ModelDownloader by lazy { ModelDownloader(this) }
    val ocr: OcrEngine by lazy { OcrEngine() }
    val translation: TranslationRepository by lazy { TranslationRepository(gemma) }

    override fun onTerminate() {
        super.onTerminate()
        runCatching { gemma.close() }
        runCatching { ocr.close() }
    }
}
