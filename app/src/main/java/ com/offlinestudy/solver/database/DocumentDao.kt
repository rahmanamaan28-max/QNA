package com.offlinestudy.solver.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doc: DocumentEntity): Long

    @Update
    suspend fun update(doc: DocumentEntity)

    @Query("SELECT * FROM documents ORDER BY dateAddedEpochMs DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE fileHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun findById(id: Long): DocumentEntity?

    @Delete
    suspend fun delete(doc: DocumentEntity)

    @Query("UPDATE documents SET indexingStatus = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    @Query("UPDATE documents SET chunkCount = :count, indexingStatus = 'INDEXED' WHERE id = :id")
    suspend fun markIndexed(id: Long, count: Int)

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun countDocuments(): Int
}
