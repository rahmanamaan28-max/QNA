package com.offlinestudy.solver.llm

/** Section 21: user/developer-configurable local model settings. */
data class LlmConfig(
    val modelPath: String,       // absolute path to a .gguf file in app-private storage
    val contextLength: Int = 2048,
    val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 6),
    val temperature: Float = 0.2f,   // low by default for factual academic answers (Section 21)
    val maxOutputTokens: Int = 512
)
