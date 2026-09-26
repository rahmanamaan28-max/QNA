package com.offlinestudy.solver.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Offline OCR via ML Kit's BUNDLED Latin text recognizer. The "bundled"
 * variant embeds its recognition model directly in the APK at build time
 * (as opposed to the Play-Services-backed "unbundled" variant, which can
 * fetch/update its model over the network) -- so this performs zero network
 * activity at runtime, satisfying the offline requirement while still using
 * a well-tested, actively-maintained recognizer instead of a hand-rolled one.
 */
class OcrEngine {
    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): OcrResult = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val lines = visionText.textBlocks.flatMap { it.lines }
                cont.resume(OcrResult(fullText = visionText.text, lines = lines.map { it.text }))
            }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    /** Blocking convenience used by PdfParser when OCR-ing scanned PDF pages off the main thread. */
    fun recognizeSync(bitmap: Bitmap): String {
        return com.google.android.gms.tasks.Tasks.await(
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
        ).text
    }

    fun close() { recognizer.close() }
}

data class OcrResult(val fullText: String, val lines: List<String>)
