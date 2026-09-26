package com.offlinestudy.solver.documents

/**
 * Cheap heuristic heading detector for PDF/TXT (DOCX gets real heading
 * styles from the XML, see DocxParser). Used to preserve "headings and
 * section names" during chunking (Section 6) even for formats with no
 * structural markup.
 */
object HeadingDetector {
    private val HEADING_LINE = Regex("^([A-Z][A-Za-z0-9 ,'&()/-]{2,60})\\s*$")

    fun guessHeading(pageText: String): String? {
        val firstLine = pageText.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        val trimmed = firstLine.trim()
        // Heuristics: short line, mostly title-cased or all-caps, no ending punctuation.
        val looksLikeHeading = trimmed.length in 3..70 &&
            !trimmed.endsWith(".") &&
            HEADING_LINE.matches(trimmed)
        return if (looksLikeHeading) trimmed else null
    }
}
