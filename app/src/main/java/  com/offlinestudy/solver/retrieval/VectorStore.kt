package com.offlinestudy.solver.retrieval

import com.offlinestudy.solver.database.AppDatabase
import com.offlinestudy.solver.database.ChunkEntity
import kotlin.math.sqrt

data class ScoredChunk(val chunk: ChunkEntity, val score: Float, val matchType: MatchType)
enum class MatchType { SEMANTIC, KEYWORD, BOTH }

/**
 * Local vector similarity search. Embeddings are already L2-normalized
 * (EmbeddingEngine), so cosine similarity reduces to a dot product.
 *
 * Implementation note: this uses brute-force in-memory search over all
 * chunks, loaded once from Room's SQLite storage into a flat FloatArray
 * matrix. That is the practical "efficient local implementation suitable
 * for mobile" for a personal study library (thousands-tens of thousands of
 * chunks); it avoids bundling a native FAISS build, which has no official
 * prebuilt Android artifact and would add substantial native-build
 * complexity for a device-local index of this size. If a user's library
 * grows large enough that brute force becomes slow, the fix is to add an
 * IVF-style coarse bucket index on top of this same table rather than
 * switching stores.
 */
class VectorStore(private val db: AppDatabase) {

    private var cache: List<ChunkEntity> = emptyList()
    private var cacheDirty = true

    suspend fun invalidate() { cacheDirty = true }

    private suspend fun ensureLoaded(): List<ChunkEntity> {
        if (cacheDirty || cache.isEmpty()) {
            cache = db.chunkDao().loadAllForIndex()
            cacheDirty = false
        }
        return cache
    }

    suspend fun semanticSearch(queryEmbedding: FloatArray, topK: Int): List<ScoredChunk> {
        val all = ensureLoaded()
        if (all.isEmpty()) return emptyList()
        return all.asSequence()
            .map { chunk -> ScoredChunk(chunk, cosine(queryEmbedding, chunk.embedding), MatchType.SEMANTIC) }
            .sortedByDescending { it.score }
            .take(topK)
            .toList()
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) dot += a[i] * b[i]
        return dot // already unit vectors -> dot product == cosine similarity
    }

    suspend fun documentCount(): Int = db.documentDao().countDocuments()
    suspend fun chunkCount(): Int = db.chunkDao().countChunks()
}
