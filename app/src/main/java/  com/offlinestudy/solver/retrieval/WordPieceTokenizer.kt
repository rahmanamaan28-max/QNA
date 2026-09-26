package com.offlinestudy.solver.retrieval

import android.content.Context

data class TokenizedInput(val inputIds: IntArray, val attentionMask: IntArray, val tokenTypeIds: IntArray)

/**
 * Minimal on-device WordPiece tokenizer matching the vocab format used by
 * MiniLM/BERT-family sentence-transformer exports. Reads vocab.txt bundled
 * in assets -- no network, no external tokenizer service.
 */
class WordPieceTokenizer(context: Context, vocabAssetPath: String) {
    private val vocab: Map<String, Int>
    private val clsId: Int
    private val sepId: Int
    private val padId: Int
    private val unkId: Int

    init {
        val lines = context.assets.open(vocabAssetPath).bufferedReader(Charsets.UTF_8).readLines()
        vocab = lines.withIndex().associate { (i, tok) -> tok to i }
        clsId = vocab["[CLS]"] ?: 101
        sepId = vocab["[SEP]"] ?: 102
        padId = vocab["[PAD]"] ?: 0
        unkId = vocab["[UNK]"] ?: 100
    }

    fun encode(text: String, maxLen: Int): TokenizedInput {
        val words = basicTokenize(text)
        val wordPieces = mutableListOf<Int>()
        for (word in words) {
            wordPieces += wordPiece(word)
            if (wordPieces.size >= maxLen - 2) break
        }

        val ids = mutableListOf(clsId)
        ids += wordPieces.take(maxLen - 2)
        ids += sepId

        val attention = IntArray(maxLen)
        val tokenType = IntArray(maxLen)
        val inputIds = IntArray(maxLen) { padId }
        for (i in ids.indices) inputIds[i] = ids[i]
        for (i in ids.indices) attention[i] = 1

        return TokenizedInput(inputIds, attention, tokenType)
    }

    private fun basicTokenize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    private fun wordPiece(word: String): List<Int> {
        if (vocab.containsKey(word)) return listOf(vocab.getValue(word))
        val pieces = mutableListOf<Int>()
        var start = 0
        var remaining = word
        var isFirst = true
        while (start < word.length) {
            var end = word.length
            var matched = false
            while (end > start) {
                val sub = (if (isFirst) "" else "##") + word.substring(start, end)
                val candidate = if (isFirst) word.substring(start, end) else "##" + word.substring(start, end)
                if (vocab.containsKey(candidate)) {
                    pieces += vocab.getValue(candidate)
                    start = end
                    isFirst = false
                    matched = true
                    break
                }
                end--
            }
            if (!matched) return listOf(unkId)
        }
        return pieces
    }
}
