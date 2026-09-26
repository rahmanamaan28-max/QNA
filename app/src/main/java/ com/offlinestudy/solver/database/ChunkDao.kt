package com.offlinestudy.solver.database

import androidx.room.*

@Dao
interface ChunkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<ChunkEntity>): List<Long>

    @Query("DELETE FROM chunks WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: Long)

    /**
     * Loaded fully into memory for brute-force cosine similarity search.
     * At the scale of a personal study library (tens of thousands of chunks,
     * each a small FloatArray) this comfortably fits in RAM and is fast
     * enough on a modern phone (sub-100ms for <20k chunks). See
     * retrieval/VectorStore.kt for the search implementation and a note on
     * scaling further with an IVF-style bucket index if needed.
     */
    @Query("SELECT * FROM chunks")
    suspend fun loadAllForIndex(): List<ChunkEntity>

    @Query("SELECT COUNT(*) FROM chunks")
    suspend fun countChunks(): Int

    @Query("""
        SELECT * FROM chunks
        WHERE chunkText LIKE '%' || :keyword || '%'
        LIMIT :limit
    """)
    suspend fun keywordSearch(keyword: String, limit: Int): List<ChunkEntity>

    @Query("SELECT * FROM chunks WHERE documentId = :documentId ORDER BY pageNumber ASC")
    suspend fun getForDocument(documentId: Long): List<ChunkEntity>
}
