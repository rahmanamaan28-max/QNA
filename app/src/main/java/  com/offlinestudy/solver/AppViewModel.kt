package com.offlinestudy.solver

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.offlinestudy.solver.database.DocumentEntity
import com.offlinestudy.solver.llm.AnswerMode
import com.offlinestudy.solver.llm.AnswerResult
import com.offlinestudy.solver.llm.ModelManagerState
import com.offlinestudy.solver.ocr.ImagePreprocessor
import com.offlinestudy.solver.ocr.QuestionSegmenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val documents: List<DocumentEntity> = emptyList(),
    val chunkCount: Int = 0,
    val indexingProgress: Map<Long, Float> = emptyMap(),
    val recognizedQuestions: List<String> = emptyList(),
    val currentAnswer: AnswerResult? = null,
    val isAnswering: Boolean = false,
    val answerMode: AnswerMode = AnswerMode.EXAM,
    val modelState: ModelManagerState? = null,
    val errorMessage: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<OfflineStudyApp>()
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            app.db.documentDao().observeAll().collect { docs ->
                _uiState.value = _uiState.value.copy(documents = docs)
            }
        }
        refreshCounts()
        refreshModelState()
    }

    fun refreshCounts() {
        viewModelScope.launch {
            val count = app.vectorStore.chunkCount()
            _uiState.value = _uiState.value.copy(chunkCount = count)
        }
    }

    fun refreshModelState() {
        _uiState.value = _uiState.value.copy(modelState = app.modelManager.getState())
    }

    fun importDocument(uri: Uri, fileName: String, mimeType: String?) {
        viewModelScope.launch {
            val result = app.documentIndexer.importAndIndex(uri, fileName, mimeType) { progress ->
                // Progress callbacks arrive off the collecting flow; safe to post directly.
            }
            app.vectorStore.invalidate()
            refreshCounts()
            when (result) {
                is com.offlinestudy.solver.documents.DocumentIndexer.Result.Failed ->
                    _uiState.value = _uiState.value.copy(errorMessage = result.reason)
                else -> Unit
            }
        }
    }

    fun reindexDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            app.documentIndexer.importAndIndex(Uri.parse(doc.uriString), doc.fileName, null, forceReindex = true)
            app.vectorStore.invalidate()
            refreshCounts()
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            app.db.documentDao().delete(doc)
            app.vectorStore.invalidate()
            refreshCounts()
        }
    }

    fun onPhotoCaptured(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch {
            val processed = ImagePreprocessor.preprocess(bitmap)
            val ocrResult = app.ocrEngine.recognize(processed)
            if (ocrResult.fullText.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Could not read the question clearly. Please retake the photo."
                )
                return@launch
            }
            val questions = QuestionSegmenter.segment(ocrResult.fullText)
            _uiState.value = _uiState.value.copy(recognizedQuestions = questions)
        }
    }

    fun setAnswerMode(mode: AnswerMode) {
        _uiState.value = _uiState.value.copy(answerMode = mode)
    }

    fun askQuestion(question: String, isFollowUp: Boolean = false) {
        val engine = app.ragEngine
        if (engine == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Offline AI model is not installed.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnswering = true)
            val result = engine.answer(question, _uiState.value.answerMode, isFollowUp)
            _uiState.value = _uiState.value.copy(isAnswering = false, currentAnswer = result)
        }
    }

    fun installLlmModel(uri: Uri) {
        val ok = app.modelManager.installLlmModelFromUri(uri)
        if (ok) app.rebuildRagEngineIfPossible()
        refreshModelState()
        if (!ok) _uiState.value = _uiState.value.copy(errorMessage = "Could not copy model file.")
    }

    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }
}
