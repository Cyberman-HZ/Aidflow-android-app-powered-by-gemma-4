package com.aidflow.pro.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit Text Recognition v2 wrapper. Runs entirely on-device. Latin script bundle
 * is included by default; additional scripts can be added by depending on the
 * corresponding `text-recognition-*` artifacts and constructing a second recognizer.
 *
 * Output preserves ML Kit's natural reading order. For dense or multi-column
 * documents the caller is expected to follow up with Gemma 4 cleanup.
 */
class OcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(context: Context, uri: Uri): Result = suspendCancellableCoroutine { cont ->
        try {
            val input = InputImage.fromFilePath(context, uri)
            recognizer.process(input)
                .addOnSuccessListener { result ->
                    val blocks = result.textBlocks.map { block ->
                        Block(
                            text = block.text,
                            lines = block.lines.map { it.text },
                        )
                    }
                    cont.resume(Result(text = result.text, blocks = blocks))
                }
                .addOnFailureListener { cont.resumeWithException(it) }
                .addOnCanceledListener { cont.cancel() }
        } catch (t: Throwable) {
            cont.resumeWithException(t)
        }
    }

    fun close() {
        runCatching { recognizer.close() }
    }

    data class Result(val text: String, val blocks: List<Block>) {
        val isEmpty: Boolean get() = text.isBlank()
        fun toLines(): List<String> = blocks.flatMap { it.lines }
    }

    data class Block(val text: String, val lines: List<String>)
}
