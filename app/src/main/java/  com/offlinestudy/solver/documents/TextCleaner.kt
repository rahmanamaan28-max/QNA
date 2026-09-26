package com.offlinestudy.solver.documents

/** Whitespace/artifact cleanup shared by all parsers (Section 5, step 3). */
object TextCleaner {
    private val MULTI_SPACE = Regex("[ \\t]{2,}")
    private val MULTI_NEWLINE = Regex("\\n{3,}")
    private val HYPHEN_LINEBREAK = Regex("-\\n(?=\\w)") // rejoin hyphenated word-wraps from PDFs

    fun clean(raw: String): String {
        var text = raw.replace("\r\n", "\n").replace('\r', '\n')
        text = text.replace(HYPHEN_LINEBREAK, "")
        text = text.replace(MULTI_SPACE, " ")
        text = text.replace(MULTI_NEWLINE, "\n\n")
        return text.trim()
    }
}
