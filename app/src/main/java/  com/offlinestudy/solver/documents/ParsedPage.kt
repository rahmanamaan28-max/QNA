package com.offlinestudy.solver.documents

/** One page (or, for TXT/DOCX where there's no real pagination, one pseudo-page). */
data class ParsedPage(
    val pageNumber: Int,       // 1-based; -1 if unknown
    val text: String,
    val headingGuess: String? = null
)

data class ParsedDocument(
    val pages: List<ParsedPage>,
    val pageCount: Int
)

interface DocumentParser {
    fun supports(fileName: String, mimeType: String?): Boolean
    /** Parses fully on-device. Must never touch the network. */
    fun parse(context: android.content.Context, uri: android.net.Uri, fileName: String): ParsedDocument
}
