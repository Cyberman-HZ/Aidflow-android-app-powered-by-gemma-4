package com.aidflow.pro.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lifecycle wrapper around the Gemma 4 LiteRT-LM Engine.
 *
 * Known sharp edges in the runtime:
 *   • Exactly one Conversation per Engine — creating a second throws.
 *   • engine.initialize() blocks several seconds, must run on IO.
 *   • CPU backend is the reliable default; GPU/NPU need device-specific drivers
 *     and stall during init on phones without OpenCL/QNN. We pin all three
 *     modalities (text, vision, audio) to CPU.
 *   • sendMessage() is non-streaming and returns a Message whose text payload
 *     is encoded inside Contents — we extract the Content.Text entries.
 */
class Gemma4Manager(private val context: Context) {

    enum class State { Idle, Loading, Ready, Error }

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var lastError: Throwable? = null
    fun lastError(): Throwable? = lastError

    private val mutex = Mutex()
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    fun isReady(): Boolean = _state.value == State.Ready && conversation != null

    suspend fun initialize(modelFile: File) = withContext(Dispatchers.IO) {
        if (_state.value == State.Loading || _state.value == State.Ready) return@withContext
        _state.value = State.Loading
        try {
            require(modelFile.exists() && modelFile.length() > 0) {
                "Model file missing or empty: ${modelFile.absolutePath}"
            }
            Log.i(TAG, "Initializing Gemma 4 from ${modelFile.absolutePath} (${modelFile.length() / 1024 / 1024} MB)")

            val cacheDir = File(context.cacheDir, "gemma").apply { mkdirs() }

            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                visionBackend = Backend.CPU(),
                audioBackend = Backend.CPU(),
                maxNumTokens = 2048,
                maxNumImages = 1,
                cacheDir = cacheDir.absolutePath,
            )

            val eng = Engine(config)
            Log.i(TAG, "Engine constructed; calling initialize()…")
            eng.initialize()
            Log.i(TAG, "Engine initialized; opening conversation…")
            engine = eng
            conversation = eng.createConversation()
            Log.i(TAG, "Gemma 4 ready")
            _state.value = State.Ready
        } catch (t: Throwable) {
            Log.e(TAG, "Gemma 4 init failed", t)
            lastError = t
            _state.value = State.Error
            safeClose()
        }
    }

    suspend fun translate(text: String, srcLang: String, dstLang: String): String {
        val prompt = Prompts.translate(srcLang, dstLang, text)
        return send(prompt).trim()
    }

    suspend fun cleanOcr(rawText: String, hint: String? = null): String {
        val prompt = Prompts.ocrCleanup(rawText, hint)
        return send(prompt).trim()
    }

    suspend fun describeImage(imageFile: File, ask: String = Prompts.imageDescribe()): String =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val conv = conversation ?: error("Gemma 4 not initialized")
                val contents = Contents.of(
                    Content.ImageFile(imageFile.absolutePath),
                    Content.Text(ask),
                )
                val reply = conv.sendMessage(contents, emptyMap<String, Any>())
                extractText(reply).trim()
            }
        }

    /** Run the family-from-text prompt and return Gemma's raw JSON output. */
    suspend fun extractFamilyFromText(description: String): String =
        send(Prompts.familyFromText(description)).trim()

    /** Run the family-from-photo prompt against a paper-form image. */
    suspend fun extractFamilyFromImage(imageFile: File): String =
        describeImage(imageFile, Prompts.familyFromImagePrompt())

    /** Run the items-identification prompt against a supply photo. */
    suspend fun identifyItemsInImage(imageFile: File): String =
        describeImage(imageFile, Prompts.identifyItemsPrompt())

    /** Resets the conversation, clearing the KV cache so prior context doesn't leak. */
    suspend fun reset() = mutex.withLock {
        val eng = engine ?: return@withLock
        runCatching { conversation?.close() }
        conversation = eng.createConversation()
    }

    fun close() {
        safeClose()
        _state.value = State.Idle
    }

    private fun safeClose() {
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        conversation = null
        engine = null
    }

    private suspend fun send(text: String): String = withContext(Dispatchers.Default) {
        mutex.withLock {
            val conv = conversation ?: error("Gemma 4 not initialized")
            val reply = conv.sendMessage(text, emptyMap<String, Any>())
            extractText(reply)
        }
    }

    private fun extractText(message: Message): String =
        message.contents.contents.asSequence()
            .filterIsInstance<Content.Text>()
            .joinToString(separator = "") { it.text }

    companion object { private const val TAG = "Gemma4Manager" }
}
