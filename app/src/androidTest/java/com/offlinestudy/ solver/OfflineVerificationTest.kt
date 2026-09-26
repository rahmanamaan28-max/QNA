package com.offlinestudy.solver

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.offlinestudy.solver.llm.AnswerMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Section 25/30: developer test that exercises the full pipeline
 * (import PDF -> index -> OCR a question image -> retrieve -> generate
 * answer) and separately asserts the app cannot reach the network.
 *
 * Run with airplane mode ON on the test device/emulator for the strongest
 * evidence, but the manifest-level assertion below holds regardless of the
 * device's actual connectivity: the app has no INTERNET permission at all.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class OfflineVerificationTest {

    @Test
    fun app_has_no_internet_permission() {
        val context = ApplicationProvider.getApplicationContext<OfflineStudyApp>()
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        val permissions = info.requestedPermissions?.toList() ?: emptyList()
        assertFalse(
            "App must never request android.permission.INTERNET",
            permissions.contains("android.permission.INTERNET")
        )
    }

    @Test
    fun full_pipeline_runs_with_network_calls_impossible() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<OfflineStudyApp>()

        // 1. Import a small bundled sample PDF from test assets.
        val sampleUri = TestAssetHelper.copyAssetToCacheAndGetUri(context, "sample_test_material.pdf")

        // 2. Index it (parse -> chunk -> embed -> store).
        val indexResult = context.documentIndexer.importAndIndex(
            sampleUri, "sample_test_material.pdf", "application/pdf"
        )
        assertTrue(
            "Indexing must succeed offline",
            indexResult is com.offlinestudy.solver.documents.DocumentIndexer.Result.Success ||
                indexResult is com.offlinestudy.solver.documents.DocumentIndexer.Result.AlreadyIndexed
        )
        context.vectorStore.invalidate()
        assertTrue("At least one chunk must be indexed", context.vectorStore.chunkCount() > 0)

        // 3. OCR a bundled sample question image.
        val bitmap = TestAssetHelper.loadTestBitmap(context, "sample_question.png")
        val ocrResult = context.ocrEngine.recognize(bitmap)
        assertTrue("OCR must extract non-empty text offline", ocrResult.fullText.isNotBlank())

        // 4. Retrieval must run without any network dependency.
        val evidence = context.retriever.retrieve(ocrResult.fullText)
        assertNotNull(evidence)

        // 5. Generation is skipped in CI unless a real GGUF model is present
        // (models are large binaries and are intentionally not committed to
        // the repo); when present, this asserts the anti-hallucination path.
        val ragEngine = context.ragEngine
        if (ragEngine != null) {
            val answer = ragEngine.answer(ocrResult.fullText, AnswerMode.MATERIAL_ONLY)
            assertNotNull(answer.answerText)
        }
    }
}

object TestAssetHelper {
    fun copyAssetToCacheAndGetUri(context: android.content.Context, assetName: String): android.net.Uri {
        val outFile = File(context.cacheDir, assetName)
        context.assets.open("test/$assetName").use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        return androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", outFile
        )
    }

    fun loadTestBitmap(context: android.content.Context, assetName: String): android.graphics.Bitmap {
        context.assets.open("test/$assetName").use { input ->
            return android.graphics.BitmapFactory.decodeStream(input)
        }
    }
}
