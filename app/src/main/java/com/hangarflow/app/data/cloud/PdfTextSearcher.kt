package com.hangarflow.app.data.cloud

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class PdfSearchHit(
    val pageNumber: Int,
    val snippet: String
)

/**
 * Extracts text per page from a PDF on first call, then runs in-memory
 * substring search. Heavy manuals (500+ pages) take a few seconds on
 * first extraction — run from a coroutine so the UI stays responsive.
 */
class PdfTextSearcher {
    private var pages: List<String>? = null

    suspend fun ensureLoaded(file: File) {
        if (pages != null) return
        pages = withContext(Dispatchers.IO) {
            runCatching {
                PDDocument.load(file).use { doc ->
                    val stripper = PDFTextStripper()
                    val count = doc.numberOfPages
                    (1..count).map { pageNum ->
                        stripper.startPage = pageNum
                        stripper.endPage = pageNum
                        runCatching { stripper.getText(doc) }.getOrDefault("")
                    }
                }
            }.getOrDefault(emptyList())
        }
    }

    fun search(query: String, maxResults: Int = 50): List<PdfSearchHit> {
        val p = pages ?: return emptyList()
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val results = mutableListOf<PdfSearchHit>()
        for ((idx, text) in p.withIndex()) {
            val pos = text.indexOf(q, ignoreCase = true)
            if (pos >= 0) {
                results += PdfSearchHit(
                    pageNumber = idx + 1,
                    snippet = extractSnippet(text, pos, q.length)
                )
                if (results.size >= maxResults) break
            }
        }
        return results
    }

    private fun extractSnippet(text: String, matchPos: Int, matchLen: Int, window: Int = 140): String {
        val halfWindow = window / 2
        val start = (matchPos - halfWindow).coerceAtLeast(0)
        val end = (matchPos + matchLen + halfWindow).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return "$prefix${text.substring(start, end).replace(Regex("\\s+"), " ").trim()}$suffix"
    }
}
