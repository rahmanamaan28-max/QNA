package com.offlinestudy.solver.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per imported source document (PDF / DOCX / TXT).
 * `fileHash` lets the indexer skip re-processing unchanged files (Section 22).
 */
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val uriString: String,
    val fileHash: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val dateAddedEpochMs: Long,
    val indexingStatus: String, // PENDING | INDEXING | INDEXED | FAILED
    val chunkCount: Int = 0
)
