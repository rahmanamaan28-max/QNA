package com.offlinestudy.solver.retrieval

import com.offlinestudy.solver.database.ChunkEntity
import kotlin.math.ln

/**
 * Lightweight BM25 keyword scorer used as the second leg of hybrid retrieval
 * (Section 9, step 4: "Optionally perform keyword/BM25 search"). Runs
 * entirely in memory over the same local chunk set -- no external search
 * service.
 */
class Bm25(private val corpus: List<ChunkEntity>, private val k1: Double = 1.5, private val b: Double = 0.75) {

    private val tokenizedDocs: List<List<String>> = corpus.map { tokenize(it.chunkText) }
    private val docLengths = tokenizedDocs.map { it.size }
    private val avgDocLength = if (docLengths.isNotEmpty()) docLengths.average() else 0.0
    private val docFreq: Map<String, Int> = run {
        val freq = HashMap<String, Int>()
        for (doc in tokenizedDocs) for (term in doc.toSet()) freq[term] = (freq[term] ?: 0) + 1
        freq
    }
    private val n = corpus.size

    fun score(query: String, topK: Int): List<Pair<ChunkEntity, Double>> {
        val queryTerms = tokenize(query)
        if (corpus.isEmpty() || queryTerms.isEmpty()) return emptyList()

        val scores = DoubleArray(corpus.size)
        for (term in queryTerms) {
            val df = docFreq[term] ?: continue
            val idf = ln(((n - df + 0.5) / (df + 0.5)) + 1.0)
            for (i in corpus.indices) {
                val tf = tokenizedDocs[i].count { it == term }
                if (tf == 0) continue
                val denom = tf + k1 * (1 - b + b * (docLengths[i] / avgDocLength))
                scores[i] += idf * (tf * (k1 + 1)) / denom
            }
        }

        return corpus.indices
            .sortedByDescending { scores[it] }
            .take(topK)
            .filter { scores[it] > 0.0 }
            .map { corpus[it] to scores[it] }
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").split(Regex("\\s+")).filter { it.length > 1 }
}
