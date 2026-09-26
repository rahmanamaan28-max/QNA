package com.offlinestudy.solver.retrieval

import com.offlinestudy.solver.documents.ParsedDocument

data class RawChunk(
    val text: String,
    val pageNumber: Int,
    val chapter: String?,
    val section: String?,
    val approxTokenCount: Int
)

/**
 * Intelligent chunking per Section 6:
 *  - ~300-800 "tokens" per chunk (approximated as whitespace-delimited words,
 *    which is a fine proxy for the small local LLMs this app targets)
 *  - overlap between consecutive chunks so a definition split across a
 *    boundary is still fully present in at least one chunk
 *  - splits preferentially at paragraph/sentence boundaries, never mid-word
 *  - keeps the page's detected heading attached as `section` metadata so it
 *    survives all the way to the citation shown in the answer
 */
class Chunker(
    private val minTokens: Int = 300,
    private val maxTokens: Int = 800,
    private val overlapTokens: Int = 60
) {
    fun chunkDocument(documentName: String, parsed: ParsedDocument): List<RawChunk> {
        val result = mutableListOf<RawChunk>()
        for (page in parsed.pages) {
            if (page.text.isBlank()) continue
            result += chunkPage(page.text, page.pageNumber, page.headingGuess)
        }
        return result
    }

    private fun chunkPage(text: String, pageNumber: Int, heading: String?): List<RawChunk> {
        // Split into paragraphs first so we never cut through a sentence if we
        // can help it; paragraphs are then greedily packed up to maxTokens.
        val paragraphs = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        if (paragraphs.isEmpty()) return emptyList()

        val chunks = mutableListOf<RawChunk>()
        var currentWords = mutableListOf<String>()

        fun flush() {
            if (currentWords.isEmpty()) return
            chunks += RawChunk(
                text = currentWords.joinToString(" "),
                pageNumber = pageNumber,
                chapter = null,
                section = heading,
                approxTokenCount = currentWords.size
            )
        }

        for (paragraph in paragraphs) {
            val words = paragraph.split(Regex("\\s+"))

            if (currentWords.size + words.size > maxTokens && currentWords.size >= minTokens) {
                flush()
                // Start next chunk with overlap from the tail of the previous one
                // so a definition/sentence spanning the boundary isn't lost.
                val overlap = currentWords.takeLast(overlapTokens)
                currentWords = overlap.toMutableList()
            }

            // A single huge paragraph longer than maxTokens on its own: split it
            // at sentence boundaries instead of mid-sentence.
            if (words.size > maxTokens) {
                flush()
                currentWords = mutableListOf()
                chunks += splitLongParagraph(paragraph, pageNumber, heading)
                continue
            }

            currentWords.addAll(words)
        }
        flush()
        return chunks
    }

    private fun splitLongParagraph(paragraph: String, pageNumber: Int, heading: String?): List<RawChunk> {
        val sentences = paragraph.split(Regex("(?<=[.!?])\\s+"))
        val chunks = mutableListOf<RawChunk>()
        var buffer = mutableListOf<String>()

        fun flush() {
            if (buffer.isEmpty()) return
            chunks += RawChunk(buffer.joinToString(" "), pageNumber, null, heading, buffer.size)
        }

        for (sentence in sentences) {
            val words = sentence.split(Regex("\\s+"))
            if (buffer.size + words.size > maxTokens && buffer.isNotEmpty()) {
                flush()
                buffer = buffer.takeLast(overlapTokens).toMutableList()
            }
            buffer.addAll(words)
        }
        flush()
        return chunks
    }
}
