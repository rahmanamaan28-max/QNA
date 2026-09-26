package com.offlinestudy.solver.retrieval

import com.offlinestudy.solver.database.AppDatabase
import com.offlinestudy.solver.database.ChunkEntity

data class RetrievedEvidence(
    val chunks: List<ScoredChunk>,
    val confidence: EvidenceConfidence
)

/** Coarse, clearly-labeled bucket -- NOT a calibrated probability (Section 10). */
enum class EvidenceConfidence { STRONG, MODERATE, WEAK, NONE }

/**
 * Combines semantic (embedding) search and BM25 keyword search, de-duplicates,
 * and re-ranks -- Section 9's hybrid retrieval pipeline.
 */
class HybridRetriever(
    private val db: AppDatabase,
    private val vectorStore: VectorStore,
    private val embeddingEngine: EmbeddingEngine,
    private val semanticWeight: Double = 0.65,
    private val keywordWeight: Double = 0.35
) {
    suspend fun retrieve(question: String, topK: Int = 6): RetrievedEvidence {
        val queryEmbedding = embeddingEngine.embed(question)
        val semanticResults = vectorStore.semanticSearch(queryEmbedding, topK = topK * 3)

        val allChunks = db.chunkDao().loadAllForIndex()
        val bm25Results = Bm25(allChunks).score(question, topK = topK * 3)

        val combined = LinkedHashMap<Long, MutableScored>()

        for (sr in semanticResults) {
            combined[sr.chunk.id] = MutableScored(sr.chunk, semantic = sr.score.toDouble())
        }
        val maxBm25 = bm25Results.maxOfOrNull { it.second } ?: 1.0
        for ((chunk, bm25score) in bm25Results) {
            val normalized = if (maxBm25 > 0) bm25score / maxBm25 else 0.0
            val existing = combined[chunk.id]
            if (existing != null) existing.keyword = normalized
            else combined[chunk.id] = MutableScored(chunk, keyword = normalized)
        }

        val ranked = combined.values
            .map {
                val finalScore = semanticWeight * it.semantic + keywordWeight * it.keyword
                val matchType = when {
                    it.semantic > 0 && it.keyword > 0 -> MatchType.BOTH
                    it.semantic > 0 -> MatchType.SEMANTIC
                    else -> MatchType.KEYWORD
                }
                ScoredChunk(it.chunk, finalScore.toFloat(), matchType)
            }
            .sortedByDescending { it.score }
            .take(topK)

        return RetrievedEvidence(ranked, estimateConfidence(ranked))
    }

    /**
     * Confidence is a simple, transparent heuristic based on top-score
     * magnitude and agreement between the two retrieval methods. Displayed
     * to the user as a coarse label, explicitly not a calibrated probability
     * (Section 10).
     */
    private fun estimateConfidence(ranked: List<ScoredChunk>): EvidenceConfidence {
        if (ranked.isEmpty()) return EvidenceConfidence.NONE
        val top = ranked.first()
        val bothAgree = ranked.take(3).count { it.matchType == MatchType.BOTH }
        return when {
            top.score >= 0.55f && bothAgree >= 1 -> EvidenceConfidence.STRONG
            top.score >= 0.35f -> EvidenceConfidence.MODERATE
            else -> EvidenceConfidence.WEAK
        }
    }

    private class MutableScored(val chunk: ChunkEntity, var semantic: Double = 0.0, var keyword: Double = 0.0)
}
