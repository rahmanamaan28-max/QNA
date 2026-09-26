package com.offlinestudy.solver

import android.app.Application
import com.offlinestudy.solver.database.AppDatabase
import com.offlinestudy.solver.documents.DocxParser
import com.offlinestudy.solver.documents.PdfParser
import com.offlinestudy.solver.documents.TextParser
import com.offlinestudy.solver.llm.LocalLlmEngine
import com.offlinestudy.solver.llm.ModelManager
import com.offlinestudy.solver.llm.RagAnswerEngine
import com.offlinestudy.solver.ocr.OcrEngine
import com.offlinestudy.solver.retrieval.Chunker
import com.offlinestudy.solver.retrieval.EmbeddingEngine
import com.offlinestudy.solver.retrieval.HybridRetriever
import com.offlinestudy.solver.retrieval.VectorStore
import com.offlinestudy.solver.documents.DocumentIndexer

/**
 * Simple hand-rolled DI container (no Hilt/Dagger, to keep the "genuinely
 * offline, no hidden magic" project easy to audit end-to-end). Every
 * component instantiated here does purely local work.
 */
class OfflineStudyApp : Application() {

    lateinit var db: AppDatabase; private set
    lateinit var ocrEngine: OcrEngine; private set
    lateinit var embeddingEngine: EmbeddingEngine; private set
    lateinit var vectorStore: VectorStore; private set
    lateinit var retriever: HybridRetriever; private set
    lateinit var modelManager: ModelManager; private set
    lateinit var documentIndexer: DocumentIndexer; private set
    var ragEngine: RagAnswerEngine? = null; private set

    override fun onCreate() {
        super.onCreate()

        db = AppDatabase.getInstance(this)
        ocrEngine = OcrEngine()
        embeddingEngine = EmbeddingEngine(this)
        vectorStore = VectorStore(db)
        retriever = HybridRetriever(db, vectorStore, embeddingEngine)
        modelManager = ModelManager(this, embeddingEngine)

        val parsers = listOf(PdfParser(ocrEngine), DocxParser(), TextParser())
        val chunker = Chunker()
        documentIndexer = DocumentIndexer(this, db, parsers, chunker, embeddingEngine)

        rebuildRagEngineIfPossible()
    }

    /** Called after a model is (re)installed via the Model Manager screen. */
    fun rebuildRagEngineIfPossible() {
        val config = modelManager.buildLlmConfig() ?: run { ragEngine = null; return }
        ragEngine = RagAnswerEngine(retriever, LocalLlmEngine(config))
    }
}
