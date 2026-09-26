package com.offlinestudy.solver.llm

import com.offlinestudy.solver.retrieval.EvidenceConfidence
import com.offlinestudy.solver.retrieval.HybridRetriever
import com.offlinestudy.solver.retrieval.ScoredChunk

data class AnswerResult(
    val question: String,
    val answerText: String,
    val sources: List<SourceRef>,
    val confidence: EvidenceConfidence,
    val notFound: Boolean
)

data class SourceRef(val documentName: String, val pageNumber: Int?, val section: String?)

/**
 * Ties retrieval (Section 9) to generation (Section 12) and enforces the
 * anti-hallucination contract (Section 10): if retrieval finds nothing
 * usable, the LLM is never even called with an empty-handed prompt that
 * invites it to guess -- the app returns the fixed refusal message itself.
 */
class RagAnswerEngine(
    private val retriever: HybridRetriever,
    private val llmEngine: LocalLlmEngine
) {
    private val history = mutableListOf<Pair<String, String>>()

    companion object {
        const val NOT_FOUND_MESSAGE = "Answer not found in the uploaded materials."
        private const val MIN_USABLE_SCORE = 0.20f
    }

    suspend fun answer(question: String, mode: AnswerMode, isFollowUp: Boolean = false): AnswerResult {
        val evidence = retriever.retrieve(question)
        val usableChunks = evidence.chunks.filter { it.score >= MIN_USABLE_SCORE }

        if (usableChunks.isEmpty()) {
            return AnswerResult(
                question = question,
                answerText = NOT_FOUND_MESSAGE,
                sources = emptyList(),
                confidence = EvidenceConfidence.NONE,
                notFound = true
            )
        }

        val prompt = PromptBuilder.buildPrompt(
            question = question,
            evidence = usableChunks,
            mode = mode,
            conversationHistory = if (isFollowUp) history.takeLast(3) else emptyList()
        )

        val generation = llmEngine.generate(prompt)
        val answerText = when (generation) {
            is LlmGenerationResult.Success -> generation.text.ifBlank { NOT_FOUND_MESSAGE }
            is LlmGenerationResult.ModelNotInstalled -> generation.message
            is LlmGenerationResult.Error -> "Error generating answer: ${generation.message}"
        }

        val isRefusal = answerText.trim().equals(NOT_FOUND_MESSAGE, ignoreCase = true)
        if (!isRefusal) history += question to answerText

        return AnswerResult(
            question = question,
            answerText = answerText,
            sources = usableChunks.map { toSourceRef(it) }.distinct(),
            confidence = evidence.confidence,
            notFound = isRefusal
        )
    }

    fun clearFollowUpContext() = history.clear()

    private fun toSourceRef(scored: ScoredChunk): SourceRef {
        val c = scored.chunk
        return SourceRef(
            documentName = c.documentName,
            pageNumber = if (c.pageNumber > 0) c.pageNumber else null,
            section = c.section
        )
    }
}
