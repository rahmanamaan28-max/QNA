package com.offlinestudy.solver.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * A single retrievable unit of text plus the metadata needed to cite it
 * (Section 5/6/13). `embedding` is stored as a flattened FloatArray blob via
 * Converters; ChunkEntity IS the vector store row (local SQLite vector store).
 */
@Entity(
    tableName = "chunks",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("documentId")]
)
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val documentName: String,
    val pageNumber: Int,          // -1 if unknown (e.g. plain TXT)
    val chapter: String?,
    val section: String?,
    val chunkText: String,
    val embedding: FloatArray,    // dimension = EmbeddingEngine.DIM
    val tokenCount: Int
) {
    override fun equals(other: Any?): Boolean = this === other || (other is ChunkEntity && id == other.id)
    override fun hashCode(): Int = id.hashCode()
}
