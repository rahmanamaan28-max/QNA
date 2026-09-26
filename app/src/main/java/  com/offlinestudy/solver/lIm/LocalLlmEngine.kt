package com.offlinestudy.solver.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class LlmGenerationResult {
    data class Success(val text: String) : LlmGenerationResult()
    data class ModelNotInstalled(val message: String = "Offline AI model is not installed.") : LlmGenerationResult()
    data class Error(val message: String) : LlmGenerationResult()
}

/**
 * Kotlin-side wrapper around a llama.cpp-based local inference engine,
 * loaded as a JNI native library (llama.android module from the llama.cpp
 * project, or an equivalent GGUF-compatible engine). All inference happens
 * in-process on-device; nothing here ever opens a socket.
 *
 * IMPORTANT / cannot be completed inside this text-only, network-disabled
 * build environment: the native `libllama_bridge.so` referenced below must
 * be compiled once, on a machine with the Android NDK, from llama.cpp's own
 * Android example (llama.cpp/examples/llama.android). That native build step
 * is unavoidable for any real on-device LLM inference engine (MLC LLM and
 * MediaPipe LLM Inference both have the same requirement) and cannot be done
 * without a C/C++ toolchain, which this sandbox does not have. The JNI
 * contract below is written to match that reference implementation so
 * dropping in the compiled .so under app/src/main/jniLibs/<abi>/ is the only
 * remaining step. See README.md "Building the native LLM engine".
 */
class LocalLlmEngine(private val config: LlmConfig) {

    private var nativeHandle: Long = 0L
    var isLoaded: Boolean = false
        private set

    fun loadModelIfNeeded(): LlmGenerationResult {
        if (isLoaded) return LlmGenerationResult.Success("")
        val file = File(config.modelPath)
        if (!file.exists()) {
            return LlmGenerationResult.ModelNotInstalled()
        }
        return try {
            nativeHandle = nativeLoadModel(config.modelPath, config.contextLength, config.threads)
            isLoaded = nativeHandle != 0L
            if (isLoaded) LlmGenerationResult.Success("") else LlmGenerationResult.Error("Native model load failed.")
        } catch (e: UnsatisfiedLinkError) {
            LlmGenerationResult.Error(
                "Native LLM library (libllama_bridge.so) is missing. Build it per README.md and place it under jniLibs/."
            )
        }
    }

    suspend fun generate(prompt: String): LlmGenerationResult = withContext(Dispatchers.Default) {
        val loadResult = loadModelIfNeeded()
        if (loadResult is LlmGenerationResult.ModelNotInstalled || loadResult is LlmGenerationResult.Error) {
            return@withContext loadResult
        }
        try {
            val text = nativeGenerate(nativeHandle, prompt, config.temperature, config.maxOutputTokens)
            LlmGenerationResult.Success(text)
        } catch (e: Exception) {
            LlmGenerationResult.Error(e.message ?: "Local inference failed.")
        }
    }

    /** Releases native memory -- called when leaving the answer screen (Section 23). */
    fun unload() {
        if (nativeHandle != 0L) {
            nativeFreeModel(nativeHandle)
            nativeHandle = 0L
            isLoaded = false
        }
    }

    // --- JNI contract, implemented by the compiled native/llama_bridge.cpp ---
    private external fun nativeLoadModel(modelPath: String, contextLength: Int, threads: Int): Long
    private external fun nativeGenerate(handle: Long, prompt: String, temperature: Float, maxTokens: Int): String
    private external fun nativeFreeModel(handle: Long)

    companion object {
        init {
            // Will throw UnsatisfiedLinkError until the native build described
            // above is placed in jniLibs/ -- handled gracefully in loadModelIfNeeded().
            try { System.loadLibrary("llama_bridge") } catch (_: UnsatisfiedLinkError) { }
        }
    }
}
