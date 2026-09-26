package com.offlinestudy.solver.documents

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipInputStream

/**
 * DOCX is a zip archive containing word/document.xml (WordprocessingML).
 * Rather than pulling in the heavy Apache POI dependency, this reads the
 * XML stream directly with the platform's built-in XmlPullParser -- fully
 * offline, no extra native code, small APK footprint.
 *
 * DOCX has no native "page number" concept (pagination is a rendering-time
 * property), so pages are synthesized: each top-level heading (styled
 * Heading1/Heading2) starts a new pseudo-page/section, which keeps chunks
 * attached to a stable, human-meaningful location for citation purposes
 * even though it isn't a literal PDF page.
 */
class DocxParser : DocumentParser {

    override fun supports(fileName: String, mimeType: String?): Boolean =
        fileName.endsWith(".docx", ignoreCase = true) ||
            mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    override fun parse(context: Context, uri: Uri, fileName: String): ParsedDocument {
        val paragraphs = mutableListOf<Pair<String?, String>>() // (headingOrNull, text)

        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        parseDocumentXml(zip, paragraphs)
                        break
                    }
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalStateException("Unable to open DOCX: $fileName")

        // Group paragraphs into pseudo-pages at each heading boundary.
        val pages = mutableListOf<ParsedPage>()
        var currentHeading: String? = null
        val buffer = StringBuilder()
        var pageNumber = 1

        fun flush() {
            val text = TextCleaner.clean(buffer.toString())
            if (text.isNotBlank()) {
                pages += ParsedPage(pageNumber = pageNumber, text = text, headingGuess = currentHeading)
                pageNumber++
            }
            buffer.clear()
        }

        for ((heading, text) in paragraphs) {
            if (heading != null) {
                flush()
                currentHeading = heading
            }
            buffer.append(text).append("\n")
        }
        flush()

        if (pages.isEmpty()) {
            pages += ParsedPage(pageNumber = 1, text = "", headingGuess = null)
        }
        return ParsedDocument(pages = pages, pageCount = pages.size)
    }

    private fun parseDocumentXml(input: java.io.InputStream, out: MutableList<Pair<String?, String>>) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, "UTF-8")

        var eventType = parser.eventType
        var inParagraph = false
        var isHeadingParagraph = false
        val paraText = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "p" -> { inParagraph = true; isHeadingParagraph = false; paraText.clear() }
                        "pStyle" -> {
                            val styleVal = parser.getAttributeValue(null, "val") ?: ""
                            if (styleVal.startsWith("Heading", ignoreCase = true) ||
                                styleVal.equals("Title", ignoreCase = true)
                            ) isHeadingParagraph = true
                        }
                        "t" -> {
                            if (parser.next() == XmlPullParser.TEXT) {
                                paraText.append(parser.text)
                            }
                            continue
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "p" && inParagraph) {
                        val text = paraText.toString()
                        out += (if (isHeadingParagraph) text.trim() else null) to text
                        inParagraph = false
                    }
                }
            }
            eventType = parser.next()
        }
    }
}
