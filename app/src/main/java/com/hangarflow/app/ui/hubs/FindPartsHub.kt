package com.hangarflow.app.ui.hubs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.cloud.HFCloudSyncService
import com.hangarflow.app.data.cloud.ManualCache
import com.hangarflow.app.data.model.HFPlane
import com.hangarflow.app.data.model.HFUserProfile
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class OrderUrgency(val raw: String, val label: String, val color: Color) {
    AOG("aog", "AOG", HFColors.StatusRed),
    Routine("normal", "Routine", HFColors.StatusBlue),
    Defer("low", "Defer", HFColors.OnSurface.copy(alpha = 0.55f))
}

@Composable
fun FindPartsHub(restrictToPlaneTail: String? = null) {
    val authState by AuthManager.state.collectAsState()
    val shopState by SharedStore.state.collectAsState()
    val orgId = authState.orgId
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<HFCloudSyncService.ManualSearchHit>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedHit by remember { mutableStateOf<HFCloudSyncService.ManualSearchHit?>(null) }
    var showOrderDialog by remember { mutableStateOf(false) }
    // Toggleable plane scope. Starts at whatever the entry point passed;
    // tech can broaden mid-flight by tapping "Search all" in the chip.
    var restrictedTail by remember { mutableStateOf(restrictToPlaneTail) }
    val cloud = remember { HFCloudSyncService() }
    val scope = rememberCoroutineScope()

    // AI parts assistant — local, ephemeral state.
    var aiAnswer by remember { mutableStateOf<String?>(null) }
    var aiLoading by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }

    // PDF state — same model as Desktop FindPartsScreen. We only swap the
    // file when it actually changes so navigating between sections in the
    // same manual just updates the page (no PdfRenderer re-init).
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var pdfPage by remember { mutableStateOf(0) }
    var pdfLoading by remember { mutableStateOf(false) }
    var pdfUnavailable by remember { mutableStateOf<String?>(null) }

    fun loadPdfFor(hit: HFCloudSyncService.ManualSearchHit) {
        val manual = shopState.manuals.firstOrNull { it.id == hit.manualId }
        if (manual == null) {
            pdfFile = null
            pdfUnavailable = "Manual file not registered for this plane"
            return
        }
        if (ManualCache.isCached(context, manual)) {
            val cached = ManualCache.localFileFor(context, manual)
            if (pdfFile?.absolutePath != cached.absolutePath) {
                pdfFile = cached
            }
            pdfPage = ((hit.pageStart ?: 1) - 1).coerceAtLeast(0)
            pdfUnavailable = null
            return
        }
        pdfFile = null
        pdfUnavailable = null
        pdfLoading = true
        scope.launch {
            try {
                val f = ManualCache.download(context, manual)
                pdfFile = f
                pdfPage = ((hit.pageStart ?: 1) - 1).coerceAtLeast(0)
            } catch (t: Throwable) {
                pdfUnavailable = "Manual PDF not available for this plane. Upload the manual to see diagrams here."
            } finally { pdfLoading = false }
        }
    }

    LaunchedEffect(selectedHit?.id) {
        selectedHit?.let { loadPdfFor(it) }
    }

    LaunchedEffect(query, orgId, restrictedTail) {
        aiAnswer = null
        aiError = null
        if (query.isBlank() || orgId == null) { hits = emptyList(); selectedHit = null; return@LaunchedEffect }
        delay(400)
        isSearching = true
        val raw = runCatching {
            cloud.searchManualReferences(orgId, query, restrictToPlaneTail = restrictedTail)
        }.getOrElse { emptyList() }

        // Dedup: same section is indexed per-plane, so one physical
        // section shows up multiple times. Prefer the row whose plane
        // actually has a registered manual (so the PDF viewer can load).
        val planesWithManual = shopState.manuals.mapNotNull { it.planeTailNumber }.toSet()
        val seen = mutableSetOf<String>()
        hits = raw.sortedByDescending { it.planeTailNumber in planesWithManual }
            .filter { hit ->
                val key = "${hit.referenceCode ?: ""}|${hit.title ?: ""}"
                if (key in seen) false else { seen += key; true }
            }
        isSearching = false
    }

    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        // LEFT — search + results list
        Column(modifier = Modifier.width(330.dp).fillMaxHeight().padding(end = 10.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Part, ATA, P/N…", color = HFColors.OnSurface.copy(alpha = 0.45f), fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
                    unfocusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
                    focusedBorderColor = HFColors.OnSurface.copy(alpha = 0.25f),
                    unfocusedBorderColor = HFColors.OnSurface.copy(alpha = 0.10f),
                    focusedTextColor = HFColors.OnSurface,
                    unfocusedTextColor = HFColors.OnSurface,
                    cursorColor = HFColors.OnSurface
                )
            )

            // Plane-scope chip — shown only when the search is restricted
            // to a single plane (entry from plane detail). Lets the tech
            // broaden to org-wide mid-flight without leaving the screen.
            restrictedTail?.let { tail ->
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF00CCDD).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFF00CCDD).copy(alpha = 0.40f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Searching only $tail",
                        color = HFColors.OnSurface.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Search all  ✕",
                        color = HFColors.OnSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(HFColors.OnSurface.copy(alpha = 0.12f))
                            .clickable { restrictedTail = null }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // AI parts assistant — above the raw hits.
            if (query.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                AIPartsPanel(
                    query = query,
                    answer = aiAnswer,
                    loading = aiLoading,
                    error = aiError,
                    onAsk = {
                        val tail = restrictedTail
                        val acType = shopState.planes.firstOrNull { it.tailNumber.equals(tail, ignoreCase = true) }?.aircraftType
                        aiLoading = true; aiError = null
                        scope.launch {
                            runCatching { cloud.aiPartsSearch(query, tail, acType) }
                                .onSuccess { aiAnswer = it.answer; aiLoading = false }
                                .onFailure { aiError = it.message ?: "AI search failed"; aiLoading = false }
                        }
                    },
                    onClear = { aiAnswer = null }
                )
            }

            if (isSearching || hits.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (isSearching) "Searching…" else "${hits.size} results",
                    color = HFColors.OnSurface.copy(alpha = 0.45f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                items(hits, key = { it.id }) { hit ->
                    val isSelected = hit.id == selectedHit?.id
                    val partCount = remember(hit.id) { extractPartNumbers(hit.bodyText).size }
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .background(if (isSelected) HFColors.OnSurface.copy(alpha = 0.10f) else Color.Transparent)
                            .clickable { selectedHit = hit }
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Text(
                            hit.title ?: hit.referenceCode ?: "Result",
                            color = HFColors.OnSurface, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 2
                        )
                        Spacer(Modifier.height(5.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            hit.referenceCode?.let {
                                AtaChip(it); Spacer(Modifier.width(5.dp))
                            }
                            hit.pageStart?.let {
                                MetaChip("p.$it"); Spacer(Modifier.width(5.dp))
                            }
                            hit.planeTailNumber?.let {
                                MetaChip(it); Spacer(Modifier.width(5.dp))
                            }
                            if (partCount > 0) MetaChip("$partCount parts")
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(HFColors.OnSurface.copy(alpha = 0.06f)))
                }
            }
        }

        Box(Modifier.width(1.dp).fillMaxHeight().background(HFColors.OnSurface.copy(alpha = 0.08f)))

        // RIGHT — detail card + PDF viewer
        Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 10.dp)) {
            val hit = selectedHit
            if (hit == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Search and select a result", color = HFColors.OnSurface.copy(alpha = 0.40f), fontSize = 13.sp)
                }
            } else {
                val planeTail = hit.planeTailNumber ?: ""
                val plane = shopState.planes.firstOrNull { it.tailNumber.equals(planeTail, ignoreCase = true) }
                val partNumbers = remember(hit.id) { extractPartNumbers(hit.bodyText) }

                // Header card
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(HFColors.OnSurface.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                hit.title ?: "Reference",
                                color = HFColors.OnSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                hit.referenceCode?.let {
                                    AtaChip(it); Spacer(Modifier.width(6.dp))
                                }
                                hit.pageStart?.let {
                                    MetaChip("p. $it"); Spacer(Modifier.width(6.dp))
                                }
                                MetaChip(
                                    planeTail.takeIf { it.isNotBlank() }?.let { tail ->
                                        plane?.displayName?.takeIf { it.isNotBlank() && it != tail }?.let { "$tail · $it" } ?: tail
                                    } ?: "—"
                                )
                                if (partNumbers.isNotEmpty()) {
                                    Spacer(Modifier.width(6.dp))
                                    MetaChip("${partNumbers.size} parts")
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        // Order Part button — iOS-style black fill + green outline
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(Color.Black)
                                .border(2.dp, HFColors.StatusGreen, RoundedCornerShape(10.dp))
                                .clickable { showOrderDialog = true }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.ShoppingCart, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Order Part", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // PDF — fills the rest. No text fallback.
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(HFColors.OnSurface.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                        .border(1.dp, HFColors.OnSurface.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                ) {
                    when {
                        pdfLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = HFColors.OnSurface.copy(alpha = 0.55f), strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Loading manual…", color = HFColors.OnSurface.copy(alpha = 0.40f), fontSize = 12.sp)
                            }
                        }
                        pdfFile != null -> InlinePdfPager(file = pdfFile!!, initialPage = pdfPage)
                        else -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Manual PDF not available",
                                    color = HFColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    pdfUnavailable ?: "Upload the manual on this plane (Manuals → Add) so techs can see diagrams and part assemblies here.",
                                    color = HFColors.OnSurface.copy(alpha = 0.55f), fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showOrderDialog && selectedHit != null && orgId != null) {
        OrderPartDialog(
            hit = selectedHit!!,
            query = query,
            planes = shopState.planes,
            techs = shopState.users,
            currentUser = shopState.currentUser,
            currentUserName = shopState.currentUser?.displayName,
            onDismiss = { showOrderDialog = false },
            onSubmit = { req ->
                showOrderDialog = false
                scope.launch {
                    runCatching {
                        cloud.createPartRequest(
                            orgId = orgId,
                            squawkId = null,
                            planeId = req.planeId,
                            planeTailNumber = req.planeTail,
                            title = req.title,
                            requestedPart = req.requestedPart,
                            urgency = req.urgency,
                            requestedBy = shopState.currentUser?.displayName,
                            quantity = req.quantity,
                            needByDate = req.needByDate,
                            assignedTechUserId = req.assignedTechUserId,
                            manualReferenceId = selectedHit!!.id,
                            notes = req.notes
                        )
                        cloud.emitOrgEvent(orgId, SharedStore.deviceIdentifier(), "part_request_created")
                        SharedStore.refresh()
                    }
                }
            }
        )
    }
}

/** Wrap the heavy ManualPdfViewer's internal PdfPagerContent in a public
 *  composable. We can't reach the private one, so we just delegate to
 *  the existing public WorkLogManualViewer/FullScreenPdfReader path is
 *  too heavy — instead embed a simple inline renderer here. */
@Composable
private fun InlinePdfPager(file: File, initialPage: Int) {
    // Reuse the existing PdfPagerContent via a thin wrapper. The simplest
    // path: lean on PdfRenderer directly with a vertical LazyColumn so we
    // don't drag in the full manual-viewer toolbar.
    SimplePdfList(file = file, initialPage = initialPage)
}

@Composable
private fun SimplePdfList(file: File, initialPage: Int) {
    val context = LocalContext.current
    var renderer by remember(file.absolutePath) { mutableStateOf<android.graphics.pdf.PdfRenderer?>(null) }
    var pageCount by remember(file.absolutePath) { mutableStateOf(0) }
    val pageAspects = remember(file.absolutePath) { mutableStateMapOf<Int, Float>() }

    DisposableEffect(file.absolutePath) {
        val pfd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        val r = android.graphics.pdf.PdfRenderer(pfd)
        renderer = r
        pageCount = r.pageCount
        onDispose { runCatching { r.close() }; runCatching { pfd.close() } }
    }

    LaunchedEffect(renderer) {
        val r = renderer ?: return@LaunchedEffect
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            synchronized(r) {
                for (i in 0 until r.pageCount) {
                    runCatching {
                        val p = r.openPage(i)
                        pageAspects[i] = if (p.height > 0) p.width.toFloat() / p.height else 0.75f
                        p.close()
                    }
                }
            }
        }
    }

    if (renderer == null || pageCount == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = HFColors.OnSurface.copy(alpha = 0.55f), strokeWidth = 2.dp)
        }
        return
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = initialPage.coerceIn(0, pageCount - 1)
    )
    LaunchedEffect(initialPage, pageCount) {
        if (pageCount > 0) listState.scrollToItem(initialPage.coerceIn(0, pageCount - 1))
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(pageCount, key = { it }) { idx ->
            val aspect = pageAspects[idx] ?: 0.75f
            InlinePdfPage(renderer = renderer!!, pageIndex = idx, aspect = aspect)
        }
    }
}

@Composable
private fun InlinePdfPage(
    renderer: android.graphics.pdf.PdfRenderer,
    pageIndex: Int,
    aspect: Float
) {
    var bitmap by remember(pageIndex) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(pageIndex) {
        bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            synchronized(renderer) {
                runCatching {
                    val page = renderer.openPage(pageIndex)
                    val targetWidth = 1800
                    val scale = targetWidth.toFloat() / page.width
                    val w = (page.width * scale).toInt()
                    val h = (page.height * scale).toInt()
                    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bmp
                }.getOrNull()
            }
        }
    }
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(aspect.coerceAtLeast(0.4f))) {
        val b = bitmap
        if (b != null) {
            androidx.compose.foundation.Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize().background(HFColors.OnSurface.copy(alpha = 0.03f)))
        }
    }
}

// ---- chips ----

private fun extractPartNumbers(body: String?): List<String> {
    if (body.isNullOrBlank()) return emptyList()
    val regex = Regex("""\b\d{2}-[A-Z]-\d{2}-\d{2}-\d{2}-\d{2}[A-Z]-\d{3}[A-Z]-[A-Z]\b""")
    return regex.findAll(body).map { it.value }.distinct().toList()
}

@Composable
private fun MetaChip(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.06f))
            .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text, color = HFColors.OnSurface.copy(alpha = 0.70f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AtaChip(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp))
            .background(HFColors.StatusGreen.copy(alpha = 0.14f))
            .border(1.dp, HFColors.StatusGreen.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text, color = HFColors.StatusGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ---- Order dialog ----

private data class OrderRequest(
    val title: String,
    val requestedPart: String,
    val quantity: Int,
    val needByDate: String?,
    val urgency: String,
    val planeId: String?,
    val planeTail: String?,
    val assignedTechUserId: String?,
    val notes: String
)

@Composable
private fun OrderPartDialog(
    hit: HFCloudSyncService.ManualSearchHit,
    query: String,
    planes: List<HFPlane>,
    techs: List<HFUserProfile>,
    currentUser: HFUserProfile?,
    currentUserName: String?,
    onDismiss: () -> Unit,
    onSubmit: (OrderRequest) -> Unit
) {
    var partName by remember(hit.id) { mutableStateOf(query.ifBlank { hit.title?.take(80) ?: "" }) }
    var quantity by remember(hit.id) { mutableStateOf("1") }
    var needByText by remember(hit.id) {
        // Default deadline to one week out so the tech only needs to clear
        // or adjust if they want a different date — saves a step on the
        // common case.
        val defaultDate = java.time.LocalDate.now().plusDays(7)
        mutableStateOf(defaultDate.toString())
    }
    var urgency by remember(hit.id) { mutableStateOf(OrderUrgency.Routine) }
    var notes by remember(hit.id) { mutableStateOf("") }
    val initialPlane = planes.firstOrNull { it.tailNumber.equals(hit.planeTailNumber ?: "", ignoreCase = true) }
    var selectedPlane by remember(hit.id) { mutableStateOf(initialPlane) }
    var selectedTech by remember(hit.id) { mutableStateOf(currentUser) }

    Dialog(onDismissRequest = onDismiss) {
        // Solid black + 12% white border. The Compose Dialog draws a
        // dimming scrim behind the content so the Find Parts screen
        // doesn't bleed through.
        Column(
            modifier = Modifier.width(520.dp)
                .background(Color.Black, RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ShoppingCart, null, tint = HFColors.OnSurface, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Order Part", color = HFColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, null, tint = HFColors.OnSurface.copy(alpha = 0.55f), modifier = Modifier.size(15.dp))
                }
            }
            Spacer(Modifier.height(14.dp))

            DialogLabel("Part name / number")
            OutlinedTextField(
                value = partName, onValueChange = { partName = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = dialogFieldColors()
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    DialogLabel("Quantity")
                    OutlinedTextField(
                        value = quantity, onValueChange = { new -> quantity = new.filter { it.isDigit() }.take(4) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = dialogFieldColors()
                    )
                }
                Column(Modifier.weight(1f)) {
                    DialogLabel("Need by (YYYY-MM-DD)")
                    OutlinedTextField(
                        value = needByText, onValueChange = { needByText = it.take(10) },
                        singleLine = true,
                        placeholder = { Text("e.g. 2026-05-20", color = HFColors.OnSurface.copy(alpha = 0.40f), fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Outlined.CalendarToday, null, tint = HFColors.OnSurface.copy(alpha = 0.40f), modifier = Modifier.size(14.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = dialogFieldColors()
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            DialogLabel("Plane")
            DropdownChooser(
                label = selectedPlane?.let {
                    val name = it.displayName.takeIf { d -> d.isNotBlank() && d != it.tailNumber }
                    if (name != null) "${it.tailNumber} · $name" else it.tailNumber
                } ?: "Select plane",
                options = planes.map { p ->
                    val name = p.displayName.takeIf { d -> d.isNotBlank() && d != p.tailNumber }
                    val display = if (name != null) "${p.tailNumber} · $name" else p.tailNumber
                    display to p
                },
                onSelect = { selectedPlane = it }
            )

            Spacer(Modifier.height(12.dp))
            DialogLabel("Tech who needs it")
            DropdownChooser(
                label = selectedTech?.displayName ?: currentUserName ?: "Anyone available",
                options = listOf<Pair<String, HFUserProfile?>>("Anyone available" to null) +
                    techs.map { t -> (t.displayName.ifBlank { t.email.ifBlank { "Tech" } }) to t },
                onSelect = { selectedTech = it }
            )

            Spacer(Modifier.height(12.dp))
            DialogLabel("Urgency")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrderUrgency.entries.forEach { u ->
                    val selected = urgency == u
                    Box(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) u.color.copy(alpha = 0.18f) else HFColors.OnSurface.copy(alpha = 0.06f))
                            .border(1.dp, if (selected) u.color else HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                            .clickable { urgency = u }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(u.label, color = if (selected) u.color else HFColors.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            DialogLabel("Notes (optional)")
            OutlinedTextField(
                value = notes, onValueChange = { notes = it.take(500) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                colors = dialogFieldColors()
            )

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", color = HFColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Box(
                    Modifier.weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(HFColors.OnSurface)
                        .clickable(enabled = partName.isNotBlank() && (quantity.toIntOrNull() ?: 0) > 0) {
                            onSubmit(
                                OrderRequest(
                                    title = partName.ifBlank { hit.title ?: "Part" }.take(140),
                                    requestedPart = partName.ifBlank { hit.title ?: "" }.take(140),
                                    quantity = quantity.toIntOrNull() ?: 1,
                                    needByDate = validateDate(needByText),
                                    urgency = urgency.raw,
                                    planeId = selectedPlane?.id,
                                    planeTail = selectedPlane?.tailNumber ?: hit.planeTailNumber,
                                    assignedTechUserId = selectedTech?.authUserId,
                                    notes = notes
                                )
                            )
                        }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Add to Parts to Order", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun validateDate(s: String): String? {
    if (s.isBlank()) return null
    return try {
        LocalDate.parse(s.trim(), DateTimeFormatter.ISO_LOCAL_DATE).toString()
    } catch (_: Throwable) { null }
}

@Composable
private fun DialogLabel(text: String) {
    Text(text, color = HFColors.OnSurface.copy(alpha = 0.45f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = HFColors.OnSurface.copy(alpha = 0.06f),
    unfocusedContainerColor = HFColors.OnSurface.copy(alpha = 0.06f),
    focusedBorderColor = HFColors.OnSurface.copy(alpha = 0.25f),
    unfocusedBorderColor = HFColors.OnSurface.copy(alpha = 0.10f),
    focusedTextColor = HFColors.OnSurface, unfocusedTextColor = HFColors.OnSurface, cursorColor = HFColors.OnSurface
)

@Composable
private fun <T> DropdownChooser(
    label: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(HFColors.OnSurface.copy(alpha = 0.06f))
                .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Person, null, tint = HFColors.OnSurface.copy(alpha = 0.40f), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = HFColors.OnSurface, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (lbl, value) ->
                DropdownMenuItem(
                    text = { Text(lbl, color = HFColors.OnSurface, fontSize = 13.sp) },
                    onClick = { onSelect(value); expanded = false }
                )
            }
        }
    }
}

// ----- AI parts assistant -----

@Composable
private fun AIPartsPanel(
    query: String,
    answer: String?,
    loading: Boolean,
    error: String?,
    onAsk: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(HFColors.OnSurface.copy(alpha = 0.05f))
            .padding(10.dp)
    ) {
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = HFColors.StatusCyan)
                Spacer(Modifier.width(8.dp))
                Text("Asking AI…", color = HFColors.OnSurface.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            answer != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✨ AI Answer", color = HFColors.StatusCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "✕",
                        color = HFColors.OnSurface.copy(alpha = 0.4f),
                        fontSize = 13.sp,
                        modifier = Modifier.clip(CircleShape).clickable(onClick = onClear).padding(4.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    AIMarkdownText(answer)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Advisory — verify against the manual + serial effectivity before ordering.",
                    color = HFColors.OnSurface.copy(alpha = 0.35f), fontSize = 9.sp
                )
            }
            else -> Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(HFColors.StatusCyan.copy(alpha = 0.12f))
                    .border(1.dp, HFColors.StatusCyan.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onAsk).padding(vertical = 9.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("✨ Ask AI", color = HFColors.StatusCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Text(error, color = HFColors.StatusRed, fontSize = 10.sp)
        }
    }
}

/** Minimal line-based markdown renderer — ## headers, - bullets,
 *  > callouts, --- rules, inline **bold**. Function avoids tables. */
@Composable
private fun AIMarkdownText(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        text.split("\n").forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Spacer(Modifier.height(2.dp))
                line.startsWith("### ") -> Text(parseInlineBold(line.removePrefix("### ")), color = HFColors.OnSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                line.startsWith("## ") -> Text(parseInlineBold(line.removePrefix("## ")), color = HFColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                line.startsWith("# ") -> Text(parseInlineBold(line.removePrefix("# ")), color = HFColors.OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                line.startsWith("- ") || line.startsWith("* ") -> Row(verticalAlignment = Alignment.Top) {
                    Text("•", color = HFColors.StatusCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(5.dp))
                    Text(parseInlineBold(line.drop(2)), color = HFColors.OnSurface.copy(alpha = 0.78f), fontSize = 11.sp)
                }
                line.startsWith("> ") -> Text(parseInlineBold(line.removePrefix("> ")), color = HFColors.StatusYellow, fontSize = 10.sp)
                line.startsWith("---") -> Box(Modifier.fillMaxWidth().height(1.dp).background(HFColors.OnSurface.copy(alpha = 0.12f)))
                else -> Text(parseInlineBold(line), color = HFColors.OnSurface.copy(alpha = 0.78f), fontSize = 11.sp)
            }
        }
    }
}

private fun parseInlineBold(s: String): AnnotatedString = buildAnnotatedString {
    s.split("**").forEachIndexed { idx, part ->
        if (idx % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
        else append(part)
    }
}
