package com.offlinestudy.solver.llm

import com.offlinestudy.solver.retrieval.ScoredChunk

/**
 * Builds the anti-hallucination system + user prompt described in
 * Sections 10 and 12. Every retrieved chunk's source/page is embedded
 * directly in the reference block so the LLM has a citation string to
 * copy, rather than needing to invent one.
 */
object PromptBuilder {

    private const val SYSTEM_PROMPT = """You are an academic study assistant.

Answer the user's question using ONLY the provided reference material below.

Rules:
1. Prioritize and rely on the supplied material above anything else.
2. In Material Only mode, use ONLY the supplied material and nothing else.
3. Never invent facts that are not supported by the reference material.
4. Never invent source document names or page numbers; only cite the exact "Source: ... Page: ..." labels given below.
5. If the evidence is insufficient to answer, respond exactly with: "Answer not found in the uploaded materials."
6. Keep the answer scientifically precise.
7. Match the requested answer length/mode.
8. Use clear headings and bullet points when appropriate for the mode.
9. Preserve scientific names, terminology, units and numerical values exactly as given in the material.
10. Clearly distinguish information directly stated in the material from any reasonable inference drawn from it, if you make one.
"""

    fun buildPrompt(
        question: String,
        evidence: List<ScoredChunk>,
        mode: AnswerMode,
        conversationHistory: List<Pair<String, String>> = emptyList() // (question, answer) pairs for follow-ups
    ): String {
        val referenceBlock = if (evidence.isEmpty()) {
            "(No relevant material was retrieved from the uploaded documents.)"
        } else {
            evidence.joinToString("\n\n") { scored ->
                val c = scored.chunk
                val pageLabel = if (c.pageNumber > 0) "Page: ${c.pageNumber}" else "Page information unavailable"
                val sectionLabel = c.section?.let { " | Section: $it" } ?: ""
                "Source: ${c.documentName} | $pageLabel$sectionLabel\n${c.chunkText}"
            }
        }

        val historyBlock = if (conversationHistory.isEmpty()) "" else {
            "PRIOR CONVERSATION (for follow-up context only, still respect the rules above):\n" +
                conversationHistory.joinToString("\n") { (q, a) -> "Q: $q\nA: $a" } + "\n\n"
        }

        return buildString {
            append(SYSTEM_PROMPT)
            append("\n\nREFERENCE MATERIAL:\n\n")
            append(referenceBlock)
            append("\n\n")
            append(historyBlock)
            append("QUESTION:\n$question\n\n")
            append("ANSWER MODE: ${mode.label} -- ${mode.instruction}\n\n")
            append("Generate the answer now, following all rules above.")
        }
    }
}
