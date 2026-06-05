package com.hangarflow.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.hangarflow.app.data.model.HFPlane
import com.hangarflow.app.data.model.HFSquawk
import com.hangarflow.app.data.model.HFWorkLog
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Per-plane PDF export for work logs + squawks. Mirrors the macOS
 * `HFDesktopPDFExport` output (auto-fit business-name header, status label,
 * title, wrapped detail) using Android's built-in `PdfDocument` so there's
 * no external dependency. Writes to the cache and opens the share sheet via
 * the existing FileProvider.
 */
object PdfExporter {

    private data class PdfRow(val status: String, val title: String, val detail: String)

    fun exportWorkLogs(context: Context, plane: HFPlane, workLogs: List<HFWorkLog>, businessName: String?) {
        val rows = workLogs.map { PdfRow(it.status, it.title.ifBlank { "(no title)" }, it.details) }
        build(context, plane, "Work Logs", businessName, rows, "WorkLogs")
    }

    fun exportSquawks(context: Context, plane: HFPlane, squawks: List<HFSquawk>, businessName: String?) {
        val rows = squawks.map { PdfRow(it.status, it.title.ifBlank { "(no title)" }, it.notes) }
        build(context, plane, "Squawks", businessName, rows, "Squawks")
    }

    private fun build(
        context: Context,
        plane: HFPlane,
        docLabel: String,
        businessName: String?,
        rows: List<PdfRow>,
        fileTag: String
    ) {
        val pageWidth = 612
        val pageHeight = 792
        val margin = 36f

        val doc = PdfDocument()
        val titlePaint = Paint().apply { color = Color.BLACK; isFakeBoldText = true; isAntiAlias = true }
        val subPaint = Paint().apply { color = Color.DKGRAY; textSize = 11f; isAntiAlias = true }
        val rowTitlePaint = Paint().apply { color = Color.BLACK; textSize = 12f; isFakeBoldText = true; isAntiAlias = true }
        val rowStatusPaint = Paint().apply { color = Color.rgb(90, 90, 90); textSize = 9f; isFakeBoldText = true; isAntiAlias = true }
        val rowBodyPaint = Paint().apply { color = Color.rgb(70, 70, 70); textSize = 10f; isAntiAlias = true }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.6f }

        // Auto-fit the business name from 22pt down to 14pt.
        val name = businessName?.takeIf { it.isNotBlank() } ?: "Hangar Flow"
        var ts = 22f
        titlePaint.textSize = ts
        while (ts > 14f && titlePaint.measureText(name) > pageWidth - margin * 2) {
            ts -= 1f
            titlePaint.textSize = ts
        }

        val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = margin + ts

        canvas.drawText(name, margin, y, titlePaint)
        y += 16f
        canvas.drawText("$docLabel for ${plane.tailNumber} — ${plane.displayName}", margin, y, subPaint)
        y += 12f
        canvas.drawText("Generated $date", margin, y, subPaint)
        y += 8f
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 18f

        rows.forEach { r ->
            if (y > pageHeight - margin - 40) {
                doc.finishPage(page)
                pageNum += 1
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = margin + 12f
            }
            canvas.drawText(r.status.uppercase(), margin, y, rowStatusPaint)
            y += 14f
            canvas.drawText(ellipsize(r.title, rowTitlePaint, pageWidth - margin * 2), margin, y, rowTitlePaint)
            y += 14f
            if (r.detail.isNotBlank()) {
                wrap(r.detail, rowBodyPaint, pageWidth - margin * 2).take(3).forEach { ln ->
                    canvas.drawText(ln, margin, y, rowBodyPaint)
                    y += 13f
                }
            }
            y += 8f
        }
        doc.finishPage(page)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "HangarFlow-${plane.tailNumber}-$fileTag-$date.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Hangar Flow $docLabel — ${plane.tailNumber}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, "Export $docLabel").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.replace("\n", " ").split(" ").filter { it.isNotBlank() }
        val lines = mutableListOf<String>()
        var cur = StringBuilder()
        words.forEach { w ->
            val candidate = if (cur.isEmpty()) w else "$cur $w"
            if (paint.measureText(candidate) <= maxWidth) {
                cur = StringBuilder(candidate)
            } else {
                if (cur.isNotEmpty()) lines.add(cur.toString())
                cur = StringBuilder(w)
            }
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        return lines
    }
}
