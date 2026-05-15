package com.aidflow.pro.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Downloads the Gemma 4 E2B .litertlm checkpoint on first launch, with resume support
 * via the HTTP Range header so a dropped Wi-Fi connection isn't fatal.
 *
 * The download is large (~3 GB), so we strongly prefer Wi-Fi by default and emit
 * granular progress for the UI.
 */
class ModelDownloader(private val context: Context) {

    /** Where the model file should end up. */
    val modelFile: File =
        File(context.filesDir, "models/$MODEL_FILENAME").also { it.parentFile?.mkdirs() }

    sealed interface State {
        data object Idle : State
        data object CheckingNetwork : State
        data class Downloading(val bytes: Long, val total: Long) : State {
            val fraction: Float get() = if (total > 0) bytes.toFloat() / total else 0f
        }
        data object Verifying : State
        data object Ready : State
        data class Error(val message: String, val cause: Throwable? = null) : State
    }

    private val _state = MutableStateFlow<State>(
        if (modelFile.exists() && modelFile.length() > MIN_PLAUSIBLE_SIZE) State.Ready else State.Idle
    )
    val state: StateFlow<State> = _state.asStateFlow()

    fun isReady(): Boolean = _state.value == State.Ready

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS) // unlimited overall — big file
        .retryOnConnectionFailure(true)
        .build()

    suspend fun download(requireUnmetered: Boolean = true) = withContext(Dispatchers.IO) {
        if (isReady()) return@withContext

        _state.value = State.CheckingNetwork
        if (requireUnmetered && !isUnmeteredNetwork()) {
            _state.value = State.Error("Please connect to Wi-Fi to download the model.")
            return@withContext
        }

        try {
            val existing = if (modelFile.exists()) modelFile.length() else 0L
            val builder = Request.Builder().url(MODEL_URL)
            if (existing > 0) builder.header("Range", "bytes=$existing-")

            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 206) {
                    throw IllegalStateException("HTTP ${resp.code} — ${resp.message}")
                }
                val body = resp.body ?: error("Empty body")
                val contentLength = body.contentLength()
                val total = when {
                    resp.code == 206 -> existing + contentLength
                    contentLength > 0 -> contentLength
                    else -> -1L
                }

                _state.value = State.Downloading(existing, total)

                RandomAccessFile(modelFile, "rw").use { raf ->
                    raf.seek(if (resp.code == 206) existing else 0)
                    body.source().use { source ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var written = if (resp.code == 206) existing else 0L
                        var lastEmit = System.currentTimeMillis()
                        val sink = raf.channel
                        while (true) {
                            val n = source.read(buffer)
                            if (n == -1) break
                            raf.write(buffer, 0, n)
                            written += n
                            val now = System.currentTimeMillis()
                            if (now - lastEmit > 200) {
                                _state.value = State.Downloading(written, total)
                                lastEmit = now
                            }
                        }
                        sink.force(false)
                        _state.value = State.Downloading(written, total)
                    }
                }
            }

            _state.value = State.Verifying
            if (modelFile.length() < MIN_PLAUSIBLE_SIZE) {
                throw IllegalStateException(
                    "Downloaded file is implausibly small (${modelFile.length()} bytes). " +
                    "The server may have returned an error page."
                )
            }
            // If we ever pin a SHA-256, verify it here. Skipping by default because
            // the published checkpoint hash may change as the upstream repo updates.

            _state.value = State.Ready
        } catch (t: Throwable) {
            Log.e(TAG, "Model download failed", t)
            _state.value = State.Error(t.message ?: "Unknown error", t)
        }
    }

    fun cancel() {
        // OkHttp call cancellation handled by closing the coroutine; expose hook for UI.
        _state.value = State.Idle
    }

    fun deleteModel(): Boolean {
        val deleted = modelFile.delete()
        if (deleted) _state.value = State.Idle
        return deleted
    }

    @Suppress("UNUSED_PARAMETER")
    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                val n = input.read(buf)
                if (n == -1) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isUnmeteredNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val nw = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(nw) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val TAG = "ModelDownloader"
        const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
        const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$MODEL_FILENAME"
        private const val BUFFER_SIZE = 64 * 1024
        // 1 GB — anything smaller is almost certainly a truncated or error response
        private const val MIN_PLAUSIBLE_SIZE = 1L * 1024 * 1024 * 1024
    }
}
