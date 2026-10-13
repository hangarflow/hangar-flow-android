package com.hangarflow.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
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

    private data class PdfRow(
        val status: String,
        val title: String,
        val detail: String,
        /** Set when the owner has already ruled on this item -- such a row
         *  prints the answer instead of tick boxes. */
        val ownerDecisionLabel: String? = null
    )

    fun exportWorkLogs(context: Context, plane: HFPlane, workLogs: List<HFWorkLog>, businessName: String?) {
        val rows = workLogs.map { PdfRow(it.status, it.title.ifBlank { "(no title)" }, it.details) }
        build(context, plane, "Work Logs", businessName, rows, "WorkLogs")
    }

    /**
     * The squawk list as a PDF the owner can act on.
     *
     * `approvalUrl` turns it into an approval sheet: APPROVE / DECLINE boxes
     * on every undecided row, a reason rule, and a QR block.
     *
     * A PDF ALONE IS A DEAD END -- the owner ticks a box and the server never
     * hears about it -- so the sheet carries the same token the email sends.
     * An owner who would rather tap than post gets their answer applied
     * automatically; the boxes stay for the ones who want paper.
     */
    fun exportSquawks(
        context: Context,
        plane: HFPlane,
        squawks: List<HFSquawk>,
        businessName: String?,
        logoBytes: ByteArray? = null,
        approvalUrl: String? = null,
        approvalExpiryLabel: String? = null
    ) {
        val rows = squawks.map {
            PdfRow(
                it.status, it.title.ifBlank { "(no title)" }, it.notes,
                ownerDecisionLabel = when (it.ownerDecision) {
                    "approved" -> "Owner approved"
                    "denied" -> "Owner declined"
                    else -> null
                }
            )
        }
        build(
            context, plane, if (approvalUrl != null) "Squawk Approval" else "Squawks",
            businessName, rows, "Squawks", logoBytes, approvalUrl, approvalExpiryLabel
        )
    }

    private fun build(
        context: Context,
        plane: HFPlane,
        docLabel: String,
        businessName: String?,
        rows: List<PdfRow>,
        fileTag: String,
        logoBytes: ByteArray? = null,
        approvalUrl: String? = null,
        approvalExpiryLabel: String? = null
    ) {
        val approval = approvalUrl?.takeIf { it.isNotBlank() }
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
        val boxPaint = Paint().apply {
            color = Color.rgb(60, 60, 60); style = Paint.Style.STROKE
            strokeWidth = 0.9f; isAntiAlias = true
        }
        val boxLabelPaint = Paint().apply { color = Color.rgb(90, 90, 90); textSize = 7.5f; isAntiAlias = true }
        val rulePaint = Paint().apply { color = Color.rgb(190, 190, 190); strokeWidth = 0.5f }
        val hintPaint = Paint().apply { color = Color.rgb(120, 120, 120); textSize = 8f; isAntiAlias = true }
        val qrHeadPaint = Paint().apply { color = Color.BLACK; textSize = 12f; isFakeBoldText = true; isAntiAlias = true }
        val decisionPaint = Paint().apply { color = Color.rgb(70, 70, 70); textSize = 9.5f; isAntiAlias = true }

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

        // Shop logo, scaled to a fixed height. Shops upload everything from a
        // square badge to a long wordmark, so the width follows the aspect
        // ratio rather than forcing a box that squashes half of them.
        if (logoBytes != null && logoBytes.isNotEmpty()) {
            runCatching {
                val bmp: Bitmap? = BitmapFactory.decodeByteArray(logoBytes, 0, logoBytes.size)
                if (bmp != null) {
                    val h = 34f
                    val w = h * (bmp.width.toFloat() / bmp.height.toFloat())
                    val maxW = (pageWidth - margin * 2) * 0.45f
                    val drawW = if (w > maxW) maxW else w
                    val drawH = if (w > maxW) h * (maxW / w) else h
                    canvas.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height),
                                      RectF(margin, y - ts, margin + drawW, y - ts + drawH), null)
                    y += drawH + 4f
                }
            }
        }

        canvas.drawText(name, margin, y, titlePaint)
        y += 16f
        canvas.drawText("$docLabel for ${plane.tailNumber} — ${plane.displayName}", margin, y, subPaint)
        y += 12f
        canvas.drawText("Generated $date", margin, y, subPaint)
        y += 8f
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 18f

        // The boxes and their labels need real room on the right; a title
        // running underneath them is unreadable and a tick in the wrong
        // column is worse than no sheet at all.
        val decisionColumn = if (approval != null) 118f else 0f
        val textWidth = pageWidth - margin * 2 - decisionColumn

        rows.forEach { r ->
            if (y > pageHeight - margin - 40) {
                doc.finishPage(page)
                pageNum += 1
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = margin + 12f
            }
            // On an approval sheet the owner's own answer replaces the shop's
            // status: an owner has no stake in "In Progress".
            if (approval == null) {
                canvas.drawText(r.status.uppercase(), margin, y, rowStatusPaint)
                y += 14f
            }

            val titleLines = wrap(r.title, rowTitlePaint, textWidth)
            val titleTop = y
            titleLines.take(3).forEach { ln ->
                canvas.drawText(ln, margin, y, rowTitlePaint)
                y += 14f
            }
            if (r.detail.isNotBlank()) {
                wrap(r.detail, rowBodyPaint, textWidth).take(3).forEach { ln ->
                    canvas.drawText(ln, margin, y, rowBodyPaint)
                    y += 13f
                }
            }

            if (approval != null) {
                val right = pageWidth - margin
                if (r.ownerDecisionLabel != null) {
                    // Already answered: print it, and give them no box to
                    // answer twice in.
                    val w = decisionPaint.measureText(r.ownerDecisionLabel)
                    canvas.drawText(r.ownerDecisionLabel, right - w, titleTop, decisionPaint)
                } else {
                    val box = 10f
                    val declineX = right - box
                    val approveX = declineX - 52f - box
                    canvas.drawRect(approveX, titleTop - box, approveX + box, titleTop, boxPaint)
                    canvas.drawRect(declineX, titleTop - box, declineX + box, titleTop, boxPaint)
                    val aw = boxLabelPaint.measureText("APPROVE")
                    val dw = boxLabelPaint.measureText("DECLINE")
                    canvas.drawText("APPROVE", approveX + (box - aw) / 2f, titleTop - box - 3f, boxLabelPaint)
                    canvas.drawText("DECLINE", declineX + (box - dw) / 2f, titleTop - box - 3f, boxLabelPaint)

                    // Somewhere to write the reason. The shop needs the reason
                    // more than the answer, and a sheet with nowhere to put it
                    // gets a bare tick and a phone call later.
                    canvas.drawText("If declined, why:", margin, y + 2f, hintPaint)
                    val rx = margin + hintPaint.measureText("If declined, why:") + 6f
                    canvas.drawLine(rx, y + 2f, pageWidth - margin, y + 2f, rulePaint)
                    y += 10f
                }
            }
            y += 8f
        }

        // ---- the live route back ----------------------------------------
        if (approval != null) {
            if (y > pageHeight - margin - 150) {
                doc.finishPage(page)
                pageNum += 1
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = margin + 12f
            }
            y += 10f
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 16f
            val qrSize = 92f
            runCatching {
                qrBitmap(approval, 480)?.let { qr ->
                    canvas.drawBitmap(qr, Rect(0, 0, qr.width, qr.height),
                                      RectF(margin, y, margin + qrSize, y + qrSize), null)
                }
            }
            val tx = margin + qrSize + 14f
            var ty = y + 10f
            canvas.drawText("APPROVE ONLINE INSTEAD", tx, ty, qrHeadPaint)
            ty += 14f
            wrap(
                "Scan this code, or open the link below, to approve or decline each item. " +
                    "Your answer reaches the shop straight away - no need to send this sheet back.",
                rowBodyPaint, pageWidth - margin - tx
            ).forEach { ln -> canvas.drawText(ln, tx, ty, rowBodyPaint); ty += 12f }
            ty += 2f
            val urlPaint = Paint().apply { color = Color.rgb(70, 70, 70); textSize = 7.5f; isAntiAlias = true }
            // A URL is one unbroken token, so the word-wrapper cannot split it
            // and it would be drawn straight off the right edge.
            wrapHard(approval, urlPaint, pageWidth - margin - tx).forEach { ln ->
                canvas.drawText(ln, tx, ty, urlPaint); ty += 9f
            }
            approvalExpiryLabel?.takeIf { it.isNotBlank() }?.let {
                ty += 2f; canvas.drawText(it, tx, ty, hintPaint); ty += 10f
            }
            y = maxOf(y + qrSize, ty) + 12f
            canvas.drawText("No prices are shown here. A quote or invoice follows separately.",
                            margin, y, hintPaint)
            y += 26f
            canvas.drawLine(margin, y, margin + 200f, y, boxPaint)
            canvas.drawLine(pageWidth - margin - 200f, y, pageWidth - margin, y, boxPaint)
            y += 11f
            canvas.drawText("Owner signature", margin, y, hintPaint)
            canvas.drawText("Date", pageWidth - margin - 200f, y, hintPaint)
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

    /**
     * The approval link as a QR bitmap.
     *
     * Error correction M, not L: this gets printed, photographed off a desk
     * and scanned at an angle in a hangar. A code that only reads under
     * perfect conditions sends the owner back to the telephone.
     */
    private fun qrBitmap(content: String, px: Int): Bitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, px, px, hints)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        for (x in 0 until px) for (yy in 0 until px) {
            bmp.setPixel(x, yy, if (matrix[x, yy]) Color.BLACK else Color.WHITE)
        }
        bmp
    }.getOrNull()

    /** Character-level wrap, for strings with nothing to break on (a URL). */
    private fun wrapHard(text: String, paint: Paint, maxWidth: Float): List<String> {
        val out = mutableListOf<String>()
        var cur = StringBuilder()
        for (ch in text) {
            if (paint.measureText(cur.toString() + ch) > maxWidth && cur.isNotEmpty()) {
                out.add(cur.toString()); cur = StringBuilder(ch.toString())
            } else cur.append(ch)
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
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
