package com.offlinestudy.solver.documents

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Plain .txt files have no pages, so text is split into fixed-size
 * pseudo-pages (roughly one screen of text each) purely so downstream
 * chunking/citation code has a uniform "pageNumber" to attach to every
 * chunk. This does NOT invent real page numbers -- the UI always labels
 * these as "Section N" rather than "Page N" (see PromptBuilder/AnswerCard).
 */
class TextParser : DocumentParser {

    companion object { private const val CHARS_PER_PSEUDO_PAGE = 2500 }

    override fun supports(fileName: String, mimeType: String?): Boolean =
        fileName.endsWith(".txt", ignoreCase = true) || mimeType == "text/plain"

    override fun parse(context: Context, uri: Uri, fileName: String): ParsedDocument {
        val fullText = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).forEachLine {
                fullText.append(it).append("\n")
            }
        } ?: throw IllegalStateException("Unable to open TXT: $fileName")

        val cleaned = TextCleaner.clean(fullText.toString())
        val pages = mutableListOf<ParsedPage>()
        var idx = 0
        var pageNumber = 1
        while (idx < cleaned.length) {
            val end = minOf(idx + CHARS_PER_PSEUDO_PAGE, cleaned.length)
            val slice = cleaned.substring(idx, end)
            pages += ParsedPage(pageNumber = pageNumber, text = slice, headingGuess = HeadingDetector.guessHeading(slice))
            idx = end
            pageNumber++
        }
        if (pages.isEmpty()) pages += ParsedPage(1, "", null)
        return ParsedDocument(pages = pages, pageCount = pages.size)
    }
}
