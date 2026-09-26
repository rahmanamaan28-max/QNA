package com.offlinestudy.solver.documents

import android.content.Context
import android.net.Uri
import com.offlinestudy.solver.database.AppDatabase
import com.offlinestudy.solver.database.ChunkEntity
import com.offlinestudy.solver.database.DocumentEntity
import com.offlinestudy.solver.retrieval.Chunker
import com.offlinestudy.solver.retrieval.EmbeddingEngine
import java.security.MessageDigest

/**
 * Orchestrates: parse -> clean (done in parsers) -> chunk -> embed -> store.
 * Implements the incremental-indexing rule from Section 22: a document whose
 * content hash hasn't changed is never re-processed.
 *
 * Progress is reported via [onProgress] (0f..1f) so the UI can show the
 * "Indexing: ████████░░ 75%" bar from Section 23.
 */
class DocumentIndexer(
    private val context: Context,
    private val db: AppDatabase,
    private val parsers: List<DocumentParser>,
    private val chunker: Chunker,
    private val embeddingEngine: EmbeddingEngine
) {
    sealed class Result {
        data class AlreadyIndexed(val documentId: Long) : Result()
        data class Success(val documentId: Long, val chunkCount: Int) : Result()
        data class Failed(val reason: String) : Result()
    }

    suspend fun importAndIndex(
        uri: Uri,
        fileName: String,
        mimeType: String?,
        forceReindex: Boolean = false,
        onProgress: (Float) -> Unit = {}
    ): Result {
        val parser = parsers.firstOrNull { it.supports(fileName, mimeType) }
            ?: return Result.Failed("Unsupported file type: $fileName")

        val hash = hashOf(uri) ?: return Result.Failed("Could not read file: $fileName")
        val existing = db.documentDao().findByHash(hash)
        if (existing != null && existing.indexingStatus == "INDEXED" && !forceReindex) {
            return Result.AlreadyIndexed(existing.id)
        }

        val sizeBytes = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L

        val docEntity = existing?.copy(indexingStatus = "INDEXING")
            ?: DocumentEntity(
                fileName = fileName,
                uriString = uri.toString(),
                fileHash = hash,
                sizeBytes = sizeBytes,
                pageCount = 0,
                dateAddedEpochMs = System.currentTimeMillis(),
                indexingStatus = "INDEXING"
            )
        val documentId = db.documentDao().insert(docEntity)

        return try {
            val parsed = parser.parse(context, uri, fileName)
            db.chunkDao().deleteForDocument(documentId) // safe no-op for new docs; clears stale chunks on re-index

            val rawChunks = chunker.chunkDocument(fileName, parsed)
            val total = rawChunks.size.coerceAtLeast(1)
            val entities = ArrayList<ChunkEntity>(rawChunks.size)

            rawChunks.forEachIndexed { index, chunk ->
                val embedding = embeddingEngine.embed(chunk.text)
                entities += ChunkEntity(
                    documentId = documentId,
                    documentName = fileName,
                    pageNumber = chunk.pageNumber,
                    chapter = chunk.chapter,
                    section = chunk.section,
                    chunkText = chunk.text,
                    embedding = embedding,
                    tokenCount = chunk.approxTokenCount
                )
                onProgress((index + 1) / total.toFloat())
            }

            db.chunkDao().insertAll(entities)
            db.documentDao().update(
                docEntity.copy(id = documentId, pageCount = parsed.pageCount, indexingStatus = "INDEXED")
            )
            db.documentDao().markIndexed(documentId, entities.size)
            Result.Success(documentId, entities.size)
        } catch (e: Exception) {
            db.documentDao().setStatus(documentId, "FAILED")
            Result.Failed(e.message ?: "Unknown indexing error")
        }
    }

    private fun hashOf(uri: Uri): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        null
    }
}
