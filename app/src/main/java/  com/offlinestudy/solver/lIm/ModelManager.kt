package com.offlinestudy.solver.llm

import android.content.Context
import com.offlinestudy.solver.retrieval.EmbeddingEngine
import java.io.File

enum class ModelStatus { INSTALLED, MISSING }

data class ModelManagerState(
    val ocrStatus: ModelStatus,      // ML Kit bundled recognizer ships inside the APK -> always INSTALLED
    val embeddingStatus: ModelStatus,
    val llmStatus: ModelStatus,
    val llmModelPath: String?
)

/**
 * Section 20: reports install status of every offline model and refuses to
 * silently fetch anything. Models are only ever copied in from local device
 * storage (Storage Access Framework) by the user -- never downloaded by the
 * app itself at runtime.
 */
class ModelManager(private val context: Context, private val embeddingEngine: EmbeddingEngine) {

    private val modelsDir: File get() = File(context.filesDir, "models").apply { mkdirs() }

    fun currentLlmModelFile(): File = File(modelsDir, "local-model.gguf")

    fun getState(): ModelManagerState {
        val llmFile = currentLlmModelFile()
        return ModelManagerState(
            ocrStatus = ModelStatus.INSTALLED, // bundled in APK, see ocr/OcrEngine.kt
            embeddingStatus = if (embeddingEngine.isModelInstalled) ModelStatus.INSTALLED else ModelStatus.MISSING,
            llmStatus = if (llmFile.exists()) ModelStatus.INSTALLED else ModelStatus.MISSING,
            llmModelPath = if (llmFile.exists()) llmFile.absolutePath else null
        )
    }

    /**
     * Copies a GGUF file the user picked via the system file picker (Storage
     * Access Framework) into app-private storage. This is a local file copy,
     * never a network download -- satisfying "Do not download models
     * automatically while answering questions."
     */
    fun installLlmModelFromUri(uri: android.net.Uri): Boolean = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            currentLlmModelFile().outputStream().use { output -> input.copyTo(output) }
        }
        true
    } catch (e: Exception) {
        false
    }

    fun buildLlmConfig(): LlmConfig? {
        val file = currentLlmModelFile()
        if (!file.exists()) return null
        return LlmConfig(modelPath = file.absolutePath)
    }
}
