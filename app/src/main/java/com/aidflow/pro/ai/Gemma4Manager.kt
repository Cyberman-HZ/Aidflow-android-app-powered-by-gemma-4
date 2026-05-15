package com.aidflow.pro.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.Method

/**
 * Lifecycle wrapper around the Gemma 4 LiteRT-LM Engine.
 *
 * The runtime has a few sharp edges (see plan notes 1–5):
 *   • Exactly one Conversation per Engine — creating a second throws.
 *   • engine.initialize() blocks several seconds, must run on IO.
 *   • CPU backend is the reliable default; GPU has OpenCL issues in early builds.
 *   • sendMessage() is non-streaming for now (streaming reportedly flaky).
 *
 * We use reflection to bind to LiteRT-LM so that minor API drift between the
 * `latest.release` resolved at build time and our compile-time understanding
 * doesn't break the build. The wrapper exposes a stable Kotlin surface.
 */
class Gemma4Manager(private val context: Context) {

    enum class State { Idle, Loading, Ready, Error }

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var lastError: Throwable? = null
    fun lastError(): Throwable? = lastError

    private val mutex = Mutex()
    private var engine: Any? = null
    private var conversation: Any? = null

    fun isReady(): Boolean = _state.value == State.Ready && conversation != null

    suspend fun initialize(modelFile: File) = withContext(Dispatchers.IO) {
        if (_state.value == State.Loading || _state.value == State.Ready) return@withContext
        _state.value = State.Loading
        try {
            require(modelFile.exists() && modelFile.length() > 0) {
                "Model file missing or empty: ${modelFile.absolutePath}"
            }

            val configCls = Class.forName("com.google.ai.edge.litertlm.EngineConfig")
            val configCtor = configCls.constructors.firstOrNull { it.parameterCount >= 1 }
                ?: error("EngineConfig has no constructor we can use")

            // Most LiteRT-LM builds expose a (modelPath: String) constructor; fall back
            // to a no-arg + setModelPath setter pattern if that changes.
            val config = try {
                configCtor.newInstance(modelFile.absolutePath)
            } catch (_: Throwable) {
                val noArg = configCls.getDeclaredConstructor().newInstance()
                configCls.getMethod("setModelPath", String::class.java)
                    .invoke(noArg, modelFile.absolutePath)
                noArg
            }

            val engineCls = Class.forName("com.google.ai.edge.litertlm.Engine")
            val engineCtor = engineCls.getDeclaredConstructor(configCls)
            engine = engineCtor.newInstance(config)
            engineCls.getMethod("initialize").invoke(engine)

            conversation = engineCls.getMethod("createConversation").invoke(engine)
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

    suspend fun describeImage(imageFile: File, ask: String = Prompts.imageDescribe()): String {
        return sendMultimodal(imageFile, ask).trim()
    }

    /** Resets the conversation, clearing the KV cache so prior context doesn't leak. */
    suspend fun reset() = mutex.withLock {
        val conv = conversation ?: return@withLock
        runCatching {
            conv.javaClass.getMethod("reset").invoke(conv)
        }.onFailure {
            // Fall back to close+recreate
            runCatching { conv.javaClass.getMethod("close").invoke(conv) }
            val eng = engine ?: return@withLock
            conversation = eng.javaClass.getMethod("createConversation").invoke(eng)
        }
    }

    fun close() {
        safeClose()
        _state.value = State.Idle
    }

    private fun safeClose() {
        runCatching { conversation?.javaClass?.getMethod("close")?.invoke(conversation) }
        runCatching { engine?.javaClass?.getMethod("close")?.invoke(engine) }
        conversation = null
        engine = null
    }

    private suspend fun send(text: String): String = withContext(Dispatchers.Default) {
        mutex.withLock {
            val conv = conversation ?: error("Gemma 4 not initialized")
            val method = pickSendStringMethod(conv.javaClass)
            (method.invoke(conv, text) as? CharSequence)?.toString()
                ?: error("Unexpected sendMessage return type")
        }
    }

    private suspend fun sendMultimodal(imageFile: File, text: String): String =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val conv = conversation ?: error("Gemma 4 not initialized")
                try {
                    val contentCls = Class.forName("com.google.ai.edge.litertlm.Content")
                    val contentsCls = Class.forName("com.google.ai.edge.litertlm.Contents")
                    val imageFileCtor = Class.forName("com.google.ai.edge.litertlm.Content\$ImageFile")
                        .getDeclaredConstructor(String::class.java)
                    val textCtor = Class.forName("com.google.ai.edge.litertlm.Content\$Text")
                        .getDeclaredConstructor(String::class.java)

                    val img = imageFileCtor.newInstance(imageFile.absolutePath)
                    val txt = textCtor.newInstance(text)

                    val ofMethod = contentsCls.methods.first { it.name == "of" }
                    val contents = ofMethod.invoke(null, arrayOf(img, txt))

                    val sendMethod = conv.javaClass.methods.first { m ->
                        m.name == "sendMessage" && m.parameterCount == 1 &&
                            m.parameterTypes[0].isAssignableFrom(contents.javaClass.let { contentsCls })
                    }
                    (sendMethod.invoke(conv, contents) as CharSequence).toString()
                } catch (t: Throwable) {
                    // Fall back to text-only: ask the model to imagine the image isn't there.
                    Log.w(TAG, "Multimodal path failed, falling back to text", t)
                    val method = pickSendStringMethod(conv.javaClass)
                    (method.invoke(conv, text) as CharSequence).toString()
                }
            }
        }

    private fun pickSendStringMethod(cls: Class<*>): Method =
        cls.methods.first { m ->
            m.name == "sendMessage" &&
                m.parameterCount == 1 &&
                m.parameterTypes[0] == String::class.java
        }

    companion object { private const val TAG = "Gemma4Manager" }
}
