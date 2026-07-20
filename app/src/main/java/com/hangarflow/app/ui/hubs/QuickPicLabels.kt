package com.hangarflow.app.ui.hubs

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.ui.theme.HFColors
import java.io.ByteArrayOutputStream

// ---------- model ----------

enum class HFLabelSize(val title: String, val cols: Int, val rows: Int, val blurb: String) {
    Tiny("Tiny", 4, 6, "24 / page"),
    Small("Small", 3, 4, "12 / page"),
    Medium("Medium", 2, 2, "4 / page"),
    Large("Large", 1, 1, "1 / page");
    val perPage get() = cols * rows
}

data class QpLabelItem(val token: String, val name: String, val kind: String)

// ---------- QR + PDF generation ----------

private fun qrBitmap(text: String, px: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, px, px)
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.RGB_565)
    for (x in 0 until px) {
        for (y in 0 until px) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    return bmp
}

/** Build a US-Letter PDF of QR + name labels in a grid sized by [size]. */
fun buildQrLabelsPdf(items: List<QpLabelItem>, size: HFLabelSize): ByteArray {
    val doc = PdfDocument()
    val pageW = 612; val pageH = 792   // 8.5x11" @72dpi
    val margin = 36f
    val cols = size.cols; val rows = size.rows; val perPage = size.perPage
    val cellW = (pageW - margin * 2) / cols
    val cellH = (pageH - margin * 2) / rows
    val textPaint = TextPaint().apply {
        color = AndroidColor.BLACK; isAntiAlias = true
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    val imgPaint = Paint()

    var page: PdfDocument.Page? = null
    items.forEachIndexed { i, item ->
        val onPage = i % perPage
        if (onPage == 0) {
            val info = PdfDocument.PageInfo.Builder(pageW, pageH, i / perPage + 1).create()
            page = doc.startPage(info)
        }
        val canvas = page!!.canvas   // top-left origin — no vertical flip needed
        val col = onPage % cols; val row = onPage / cols
        val cellX = margin + col * cellW
        val cellY = margin + row * cellH
        val pad = 12f
        val nameH = minOf(26f, cellH * 0.14f)
        val qrAreaW = cellW - pad * 2
        val qrAreaH = cellH - pad * 2 - nameH
        val side = minOf(qrAreaW, qrAreaH).coerceAtLeast(1f)
        val qrLeft = cellX + (cellW - side) / 2
        val qrTop = cellY + pad
        val bmp = qrBitmap(item.token, 320)
        canvas.drawBitmap(bmp, null, RectF(qrLeft, qrTop, qrLeft + side, qrTop + side), imgPaint)

        val fontSize = minOf(15f, maxOf(9f, nameH * 0.66f))
        textPaint.textSize = fontSize
        val maxTextWidth = cellW - pad * 2
        val raw = item.name.ifBlank { item.token }
        val display = TextUtils.ellipsize(raw, textPaint, maxTextWidth, TextUtils.TruncateAt.END).toString()
        canvas.drawText(display, cellX + cellW / 2, qrTop + side + nameH * 0.72f, textPaint)

        if (onPage == perPage - 1 || i == items.lastIndex) {
            doc.finishPage(page)
            page = null
        }
    }
    val out = ByteArrayOutputStream()
    doc.writeTo(out)
    doc.close()
    return out.toByteArray()
}

// ---------- builder UI ----------

@Composable
fun QuickPicLabelBuilder(preselected: Set<String> = emptySet(), onBack: () -> Unit) {
    val state by SharedStore.state.collectAsState()
    val context = LocalContext.current
    var selected by remember { mutableStateOf(preselected) }
    var size by remember { mutableStateOf(HFLabelSize.Medium) }
    var pendingPdf by remember { mutableStateOf<ByteArray?>(null) }

    val equipmentItems = remember(state.equipment) {
        state.equipment.map { QpLabelItem("HFE:${it.id}", it.name.ifBlank { "Equipment" }, "Equipment") }
    }
    val partItems = remember(state.partLocations) {
        state.partLocations.map { p ->
            QpLabelItem("HFP:${p.id}", p.partName.ifBlank { p.partNumber.ifBlank { "Part" } }, "Part")
        }
    }
    val allItems = equipmentItems + partItems
    val selectedItems = allItems.filter { selected.contains(it.token) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val data = pendingPdf
        if (uri != null && data != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
            }.isSuccess
            Toast.makeText(context, if (ok) "Labels saved" else "Couldn't save", Toast.LENGTH_SHORT).show()
        }
        pendingPdf = null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Pick parts and equipment, choose a size, then save a PDF sheet to print.",
                    color = HFColors.OnSurface.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
            }
            item {
                Text("LABEL SIZE", color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    HFLabelSize.entries.forEach { s ->
                        val active = size == s
                        Column(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) HFColors.OnSurface.copy(alpha = 0.18f) else HFColors.OnSurface.copy(alpha = 0.06f))
                                .border(1.dp, if (active) HFColors.OnSurface.copy(alpha = 0.5f) else HFColors.OnSurface.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .clickable { size = s }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(s.title, color = if (active) HFColors.OnSurface else HFColors.OnSurface.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(s.blurb, color = HFColors.OnSurface.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            if (equipmentItems.isNotEmpty()) {
                item { QpSectionLabel("Equipment") }
                items(equipmentItems, key = { it.token }) { item ->
                    QpItemRow(item, selected.contains(item.token)) {
                        selected = if (selected.contains(item.token)) selected - item.token else selected + item.token
                    }
                }
            }
            if (partItems.isNotEmpty()) {
                item { QpSectionLabel("Parts") }
                items(partItems, key = { it.token }) { item ->
                    QpItemRow(item, selected.contains(item.token)) {
                        selected = if (selected.contains(item.token)) selected - item.token else selected + item.token
                    }
                }
            }
            if (allItems.isEmpty()) {
                item {
                    Text("Nothing to label yet. Add equipment or parts first.", color = HFColors.OnSurface.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Generate bar
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            val count = selectedItems.size
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (count == 0) HFColors.OnSurface.copy(alpha = 0.10f) else HFColors.OnSurface)
                    .clickable(enabled = count > 0) {
                        pendingPdf = buildQrLabelsPdf(selectedItems, size)
                        saveLauncher.launch("HangarFlow-Labels.pdf")
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (count == 0) "Select items to label" else "Save $count label${if (count == 1) "" else "s"} (PDF)",
                    color = HFColors.BrandInk, fontSize = 15.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun QpSectionLabel(text: String) {
    Text(text.uppercase(), color = HFColors.OnSurface.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
private fun QpItemRow(item: QpLabelItem, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.04f))
            .clickable(onClick = onToggle)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (checked) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked) HFColors.StatusGreen else HFColors.OnSurface.copy(alpha = 0.35f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(10.dp))
        Text(item.name, color = HFColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
    }
}
