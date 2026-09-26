package com.offlinestudy.solver.documents

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.offlinestudy.solver.ocr.OcrEngine

/**
 * Extracts text page-by-page (Section: "For PDFs, preserve document name,
 * page number, extracted text"). Pages processed one at a time (not the
 * whole PDF loaded into a single string) to respect the memory constraints
 * in Section 23.
 *
 * Scanned pages (no extractable text layer) fall back to rendering the page
 * to a bitmap and running the same offline OCR engine used for question
 * photos, per "For scanned PDFs, use local OCR."
 */
class PdfParser(private val ocrEngine: OcrEngine) : DocumentParser {

    override fun supports(fileName: String, mimeType: String?): Boolean =
        fileName.endsWith(".pdf", ignoreCase = true) || mimeType == "application/pdf"

    override fun parse(context: Context, uri: Uri, fileName: String): ParsedDocument {
        // PDFBox-Android needs its font/resource cache initialized once per process.
        PDFBoxResourceLoader.init(context)

        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { document ->
                val pageCount = document.numberOfPages
                val stripper = PDFTextStripper()
                val pages = mutableListOf<ParsedPage>()

                for (pageIndex in 0 until pageCount) {
                    val pageNumber = pageIndex + 1
                    stripper.startPage = pageNumber
                    stripper.endPage = pageNumber
                    var text = stripper.getText(document).trim()

                    if (text.length < 20) {
                        // Likely a scanned page with no text layer -> OCR it.
                        text = ocrScannedPage(document, pageIndex)
                    }

                    val cleaned = TextCleaner.clean(text)
                    pages += ParsedPage(
                        pageNumber = pageNumber,
                        text = cleaned,
                        headingGuess = HeadingDetector.guessHeading(cleaned)
                    )
                }
                return ParsedDocument(pages = pages, pageCount = pageCount)
            }
        }
        throw IllegalStateException("Unable to open PDF: $fileName")
    }

    private fun ocrScannedPage(document: PDDocument, pageIndex: Int): String {
        return try {
            val renderer = PDFRenderer(document)
            // 200dpi is enough for OCR accuracy without excessive memory use.
            val bitmap = renderer.renderImageWithDPI(pageIndex, 200f)
            val result = ocrEngine.recognizeSync(bitmap)
            bitmap.recycle()
            result
        } catch (e: Exception) {
            "" // Leave blank; retrieval simply won't find anything on this page.
        }
    }
}
