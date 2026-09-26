package com.offlinestudy.solver.retrieval

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream
import kotlin.math.sqrt

/**
 * Runs a small local sentence-transformer model (e.g. all-MiniLM-L6-v2,
 * converted to a quantized .tflite file) entirely on-device via TensorFlow
 * Lite. No network call of any kind.
 *
 * SETUP REQUIRED (cannot be done inside this offline build environment):
 * place the converted model at app/src/main/assets/models/embedding.tflite
 * and its WordPiece vocab at app/src/main/assets/models/vocab.txt.
 * See README.md "Model setup" for the one-time conversion steps.
 */
class EmbeddingEngine(context: Context) {

    companion object { const val DIM = 384 } // matches MiniLM-L6-v2 output size

    private val tokenizer = WordPieceTokenizer(context, "models/vocab.txt")
    private var interpreter: Interpreter? = null

    val isModelInstalled: Boolean

    init {
        interpreter = try {
            Interpreter(loadModelFile(context, "models/embedding.tflite"))
        } catch (e: Exception) {
            null
        }
        isModelInstalled = interpreter != null
    }

    fun embed(text: String): FloatArray {
        val interp = interpreter
            ?: throw IllegalStateException("Embedding model is not installed. See Model Manager.")

        val tokens = tokenizer.encode(text, maxLen = 128)
        val inputIds = arrayOf(tokens.inputIds)
        val attentionMask = arrayOf(tokens.attentionMask)
        val tokenTypeIds = arrayOf(tokens.tokenTypeIds)

        // Output: [1, seqLen, hiddenSize] token embeddings -> mean-pool -> L2 normalize.
        val output = Array(1) { Array(tokens.inputIds.size) { FloatArray(DIM) } }
        interp.runForMultipleInputsOutputs(
            arrayOf(inputIds, attentionMask, tokenTypeIds),
            mapOf(0 to output)
        )

        return meanPoolAndNormalize(output[0], tokens.attentionMask)
    }

    private fun meanPoolAndNormalize(tokenEmbeddings: Array<FloatArray>, mask: IntArray): FloatArray {
        val pooled = FloatArray(DIM)
        var count = 0
        for (i in tokenEmbeddings.indices) {
            if (mask[i] == 0) continue
            for (d in 0 until DIM) pooled[d] += tokenEmbeddings[i][d]
            count++
        }
        if (count > 0) for (d in 0 until DIM) pooled[d] /= count
        var norm = 0f
        for (v in pooled) norm += v * v
        norm = sqrt(norm).coerceAtLeast(1e-8f)
        for (d in 0 until DIM) pooled[d] /= norm
        return pooled
    }

    private fun loadModelFile(context: Context, assetPath: String): MappedByteBuffer {
        val afd = context.assets.openFd(assetPath)
        FileInputStream(afd.fileDescriptor).use { input ->
            return input.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }

    fun close() { interpreter?.close() }
}
