package com.offlinestudy.solver.ocr

/**
 * Splits a block of OCR'd text into individual questions when a single
 * photo contains a numbered list (Section 8). Heuristic: a new question
 * starts at a line beginning with "<number>." / "<number>)" / "Q<number>",
 * or a recognizable question-word/imperative opener when no numbering
 * exists.
 */
object QuestionSegmenter {
    private val NUMBERED_START = Regex("^\\s*(?:Q\\.?\\s*)?(\\d{1,2})[.)]\\s+")

    fun segment(rawText: String): List<String> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val questions = mutableListOf<StringBuilder>()
        for (line in lines) {
            val match = NUMBERED_START.find(line)
            if (match != null) {
                questions += StringBuilder(line.substring(match.range.last + 1))
            } else if (questions.isEmpty()) {
                questions += StringBuilder(line)
            } else {
                questions.last().append(" ").append(line)
            }
        }

        val results = questions.map { it.toString().trim() }.filter { it.isNotBlank() }
        // If no numbering was detected at all, treat the whole OCR text as one question.
        return if (results.size <= 1) listOf(rawText.trim()) else results
    }
}
