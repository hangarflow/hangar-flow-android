package com.hangarflow.app.data.cloud

import android.util.Log
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class PdfOutlineEntry(
    val label: String,
    val pageNumber: Int?,
    val depth: Int
)

/**
 * Reads the native PDF outline (bookmarks / table of contents) using
 * PDFBox-Android. iOS does the same via `PDFDocument.outlineRoot`.
 *
 * Uses temp-file-only memory setting so big manuals (159 MB Pilatus AMM)
 * don't blow the heap — PDFBox buffers to disk instead.
 */
object PdfOutlineReader {
    private const val TAG = "PdfOutlineReader"

    suspend fun read(file: File): List<PdfOutlineEntry> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Loading outline from ${file.name} (${file.length() / 1024}KB)")
            // Use temp-file buffering so the 159MB Pilatus AMM doesn't
            // blow the heap. iOS memory-maps via PDFDocument(url:).
            val memSetting = MemoryUsageSetting.setupTempFileOnly()
            val doc = PDDocument.load(file, memSetting)
            try {
                val catalog = doc.documentCatalog
                Log.i(TAG, "Got catalog, checking outline…")
                val outline = catalog.documentOutline
                if (outline == null) {
                    Log.w(TAG, "documentOutline is null — PDF has no bookmarks")
                    return@withContext emptyList<PdfOutlineEntry>()
                }
                Log.i(TAG, "Outline found, walking tree…")
                val out = mutableListOf<PdfOutlineEntry>()
                walkChildren(outline, doc, depth = 0, out = out)
                Log.i(TAG, "Extracted ${out.size} outline entries")
                out
            } finally {
                doc.close()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to read outline: ${t.javaClass.simpleName}: ${t.message}", t)
            emptyList()
        }
    }

    private fun walkChildren(
        parent: PDOutlineNode,
        doc: PDDocument,
        depth: Int,
        out: MutableList<PdfOutlineEntry>
    ) {
        try {
            var child: PDOutlineItem? = parent.firstChild
            while (child != null) {
                val label = child.title?.trim().orEmpty()
                if (label.isNotBlank()) {
                    val page = resolvePageNumber(child, doc)
                    out.add(PdfOutlineEntry(label = label, pageNumber = page, depth = depth))
                }
                // PDOutlineItem extends PDOutlineNode — it can have its
                // own children accessible via firstChild.
                walkChildren(child, doc, depth + 1, out)
                child = child.nextSibling
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error walking children at depth $depth: ${t.message}", t)
        }
    }

    private fun resolvePageNumber(item: PDOutlineItem, doc: PDDocument): Int? {
        // Try findDestinationPage first — handles named + direct dests.
        runCatching {
            val page = item.findDestinationPage(doc)
            if (page != null) {
                val idx = doc.pages.indexOf(page)
                if (idx >= 0) return idx + 1
            }
        }
        // Fallback: raw PDPageDestination.
        runCatching {
            val dest = item.destination
            if (dest is PDPageDestination) {
                val num = dest.retrievePageNumber()
                if (num >= 0) return num + 1
            }
        }
        // Fallback: PDFAction → GoTo → destination.
        runCatching {
            val action = item.action
            if (action is com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo) {
                val dest = action.destination
                if (dest is PDPageDestination) {
                    val num = dest.retrievePageNumber()
                    if (num >= 0) return num + 1
                }
            }
        }
        return null
    }
}
