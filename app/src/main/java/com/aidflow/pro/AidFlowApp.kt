package com.aidflow.pro

import android.app.Application
import android.util.Log
import com.aidflow.pro.ai.Gemma4Manager
import com.aidflow.pro.ai.ModelDownloader
import com.aidflow.pro.export.IntakeExporter
import com.aidflow.pro.ocr.OcrEngine
import com.aidflow.pro.translate.TranslationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application owns the singletons whose lifecycle should match the process:
 *   • Gemma 4 engine + conversation (expensive to recreate)
 *   • ML Kit recognizer (holds native handles)
 *
 * We construct lazily so the cold-start path remains fast — the user sees the
 * Activity before we pay any of these costs. If the Gemma model is already on
 * disk from a previous run we also kick off engine initialization in the
 * background here so it's ready by the time the user reaches a feature.
 */
class AidFlowApp : Application() {

    val gemma: Gemma4Manager by lazy { Gemma4Manager(this) }
    val modelDownloader: ModelDownloader by lazy { ModelDownloader(this) }
    val ocr: OcrEngine by lazy { OcrEngine() }
    val translation: TranslationRepository by lazy { TranslationRepository(gemma) }
    val intakeExporter: IntakeExporter by lazy { IntakeExporter(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Eagerly start engine init when the model is already cached on disk.
        // The ModelSetupScreen also triggers init when it appears, but starting
        // here too is a safety net for paths that bypass that screen (process
        // recreation after the OS reclaims memory, deep-links, etc.) — by the
        // time the user navigates to a feature, Gemma is either ready or
        // visibly loading.
        if (modelDownloader.isReady() &&
            gemma.state.value == Gemma4Manager.State.Idle) {
            Log.i(TAG, "Model on disk — kicking off eager Gemma init")
            appScope.launch {
                runCatching { gemma.initialize(modelDownloader.modelFile) }
                    .onFailure { Log.w(TAG, "Eager init failed (Setup screen will retry)", it) }
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        runCatching { gemma.close() }
        runCatching { ocr.close() }
    }

    companion object { private const val TAG = "AidFlowApp" }
}
