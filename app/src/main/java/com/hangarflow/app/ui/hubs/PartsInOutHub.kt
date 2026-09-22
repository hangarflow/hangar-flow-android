package com.hangarflow.app.ui.hubs

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.model.HFPartMovement
import com.hangarflow.app.ui.common.HFPullToRefreshHost
import com.hangarflow.app.ui.theme.HFColors
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * What landed, and what still owes a vendor.
 *
 * Two boards, because those are two different jobs on two different clocks —
 * one gets put away, the other gets shipped before a deadline.
 *
 * Layout follows the device: a tablet is wide enough to show both at once,
 * which is how the Desktop reads. A phone can't, so the two become segments
 * you switch between — the same information, one column at a time.
 */
private const val TWO_COLUMN_MIN_WIDTH_DP = 600

@Composable
fun PartsInOutHub() {
    HFPullToRefreshHost { PartsInOutContent() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartsInOutContent() {
    val state by SharedStore.state.collectAsState()
    val auth by AuthManager.state.collectAsState()
    val canManage = auth.isAdmin || auth.isLeadTech
    val today = remember { LocalDate.now() }

    var receiving by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<HFPartMovement?>(null) }
    var editing by remember { mutableStateOf<HFPartMovement?>(null) }
    var phoneBoard by remember { mutableStateOf(0) }
    // Only one row may sit swiped open at a time.
    var openSwipeId by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val movements = state.partMovements
    val inbound = remember(movements) {
        movements.filter { it.isInbound && it.status != "closed" }
            .sortedByDescending { it.receivedAt ?: it.createdAt ?: "" }
    }
    val outbound = remember(movements) {
        movements.filter { !it.isInbound && it.status !in setOf("closed", "refunded") }
            .sortedBy { it.coreDueBackBy ?: "9999" }
    }
    val outstandingCores = movements.filter { it.coreOutstanding }
    val atRisk = outstandingCores.sumOf { it.coreDepositCents ?: 0L }
    val overdue = outstandingCores.filter { it.isOverdue(today) }
    val soonest = outstandingCores.mapNotNull { it.daysRemaining(today) }.minOrNull()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth.value >= TWO_COLUMN_MIN_WIDTH_DP

        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(4.dp))
            // No title here — HubSheetHost already puts "Parts In & Out" and
            // its blurb above this content. Repeating it stacks two headers.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canManage) {
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(100.dp))
                            .background(HFColors.OnSurface.copy(alpha = 0.10f))
                            .clickable { receiving = true }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Add, null, tint = HFColors.OnSurface,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Receive", color = HFColors.OnSurface, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat("Coming In", "${inbound.size}", HFColors.StatusBlue, Modifier.weight(1f))
                MiniStat("Cores Owed", "${outstandingCores.size}", HFColors.StatusOrange, Modifier.weight(1f))
                MiniStat(
                    "At Risk",
                    if (atRisk > 0) money(atRisk) else "—",
                    if (overdue.isNotEmpty()) HFColors.StatusRed else HFColors.StatusGreen,
                    Modifier.weight(1f)
                )
            }

            if (outstandingCores.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                val urgent = overdue.isNotEmpty() || (soonest != null && soonest <= 7)
                val accent = if (overdue.isNotEmpty()) HFColors.StatusRed
                else if (urgent) HFColors.StatusOrange else HFColors.StatusBlue
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(accent.copy(alpha = 0.08f))
                        .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        "${outstandingCores.size} ${if (outstandingCores.size == 1) "core" else "cores"} " +
                            "still owed back" +
                            if (atRisk > 0) " · ${money(atRisk)} at risk" else "",
                        color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        when {
                            overdue.isNotEmpty() ->
                                "${overdue.size} past the vendor's deadline — that deposit is being written off."
                            soonest != null && soonest <= 7 ->
                                "The nearest is due back in $soonest ${if (soonest == 1L) "day" else "days"}."
                            soonest != null -> "The nearest is due back in $soonest days."
                            else -> "No deadlines recorded."
                        },
                        color = HFColors.OnSurface.copy(alpha = 0.75f), fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (wide) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Board("CAME IN", inbound, today,
                        "Nothing on its way. Log a part when it lands.",
                        Modifier.weight(1f), canManage, openSwipeId,
                        { openSwipeId = it }, { actionsFor = it }, { editing = it },
                        { m, a -> SharedStore.runPartMovementAction(m.id, a) })
                    Box(Modifier.width(1.dp).fillMaxHeight().background(HFColors.OutlineSubtle))
                    Board("NEEDS TO GO OUT", outbound, today,
                        "Nothing owed back to a vendor.",
                        Modifier.weight(1f), canManage, openSwipeId,
                        { openSwipeId = it }, { actionsFor = it }, { editing = it },
                        { m, a -> SharedStore.runPartMovementAction(m.id, a) })
                }
            } else {
                // Phone: the boards become segments. Counts stay on the chips so
                // you can see there's something on the other side without going.
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.04f)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SegmentChip("Came in", inbound.size, phoneBoard == 0,
                        urgent = false) { phoneBoard = 0 }
                    SegmentChip("Going out", outbound.size, phoneBoard == 1,
                        urgent = overdue.isNotEmpty()) { phoneBoard = 1 }
                }
                Spacer(Modifier.height(12.dp))
                if (phoneBoard == 0) {
                    Board(null, inbound, today,
                        "Nothing on its way. Log a part when it lands.",
                        Modifier.weight(1f), canManage, openSwipeId,
                        { openSwipeId = it }, { actionsFor = it }, { editing = it },
                        { m, a -> SharedStore.runPartMovementAction(m.id, a) })
                } else {
                    Board(null, outbound, today,
                        "Nothing owed back to a vendor.",
                        Modifier.weight(1f), canManage, openSwipeId,
                        { openSwipeId = it }, { actionsFor = it }, { editing = it },
                        { m, a -> SharedStore.runPartMovementAction(m.id, a) })
                }
            }
        }
    }

    // Tap a card → the actions, as thumb-sized rows. This is the touch
    // equivalent of the Desktop's right-click menu; long-press would hide the
    // whole feature behind a gesture nothing on screen advertises.
    actionsFor?.let { m ->
        ModalBottomSheet(
            onDismissRequest = { actionsFor = null },
            sheetState = sheetState,
            containerColor = HFColors.Surface,
            contentColor = HFColors.OnSurface
        ) {
            MovementActionsSheet(
                movement = m,
                canManage = canManage,
                onEdit = { actionsFor = null; editing = m },
                onAction = { action ->
                    actionsFor = null
                    SharedStore.runPartMovementAction(m.id, action)
                },
                onClose = { actionsFor = null }
            )
        }
    }

    if (receiving) {
        ReceivePartSheet(onDismiss = { receiving = false })
    }
    editing?.let { m ->
        EditMovementSheet(movement = m, onDismiss = { editing = null })
    }
}

@Composable
private fun Board(
    title: String?,
    items: List<HFPartMovement>,
    today: LocalDate,
    empty: String,
    modifier: Modifier = Modifier,
    canManage: Boolean,
    openSwipeId: String?,
    onOpenChange: (String?) -> Unit,
    onTap: (HFPartMovement) -> Unit,
    onEdit: (HFPartMovement) -> Unit,
    onAction: (HFPartMovement, String) -> Unit
) {
    Column(modifier) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = HFColors.OnSurfaceMuted, fontSize = 9.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Spacer(Modifier.width(8.dp))
                Text("${items.size}", color = HFColors.OnSurfaceMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
        if (items.isEmpty()) {
            Text(empty, color = HFColors.OnSurfaceMuted, fontSize = 12.sp)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(items, key = { it.id }) { m ->
                    if (canManage) {
                        SwipeRevealRow(
                            rowId = m.id,
                            openId = openSwipeId,
                            onOpenChange = onOpenChange,
                            leading = primaryActionFor(m)?.let { p ->
                                p.copy(run = { onAction(m, p.action) })
                            },
                            trailing = listOf(
                                SwipeAction("Edit", Icons.Outlined.Edit, HFColors.StatusBlue,
                                    "", run = { onEdit(m) }),
                                SwipeAction("Delete", Icons.Outlined.Delete, HFColors.StatusRed,
                                    "__delete", run = { onAction(m, "__delete") })
                            )
                        ) {
                            MovementCard(m, today) { onTap(m) }
                        }
                    } else {
                        MovementCard(m, today) { onTap(m) }
                    }
                }
            }
        }
    }
}

/**
 * One plate revealed behind a swiped row. `action` is the token
 * [SharedStore.runPartMovementAction] understands; Edit carries none because
 * it opens a sheet rather than writing anything.
 */
private data class SwipeAction(
    val title: String,
    val icon: ImageVector,
    val tint: Color,
    val action: String,
    val run: () -> Unit = {}
)

/**
 * The ONE thing this row's state is waiting on, for the right-swipe. Kept in
 * step with the bottom sheet, which stays the complete list.
 *
 * Null where the state has no single obvious next step — a delivered core can
 * be refunded OR rejected, and guessing between those moves the shop's money.
 * Those rows still tap through to the sheet.
 */
private fun primaryActionFor(m: HFPartMovement): SwipeAction? = when {
    m.isCore && m.status == "awaiting" ->
        SwipeAction("Mark shipped", Icons.Outlined.LocalShipping, HFColors.StatusBlue, "in_transit")
    m.isCore && m.status == "in_transit" ->
        SwipeAction("Vendor has it", Icons.Outlined.LocalShipping, HFColors.StatusBlue, "delivered")
    m.isInbound && m.status == "in_transit" ->
        SwipeAction("Mark received", Icons.Outlined.SouthWest, HFColors.StatusGreen, "received")
    // The one action that moves stock. "Fitted on arrival" is the other half
    // of the same decision and stays in the sheet — the two are easy to
    // confuse, and a swipe is the wrong place to pick between them.
    m.isInbound && m.status == "received" ->
        SwipeAction("Add to inventory", Icons.Outlined.Inventory2, HFColors.StatusGreen, "__shelf")
    else -> null
}

/**
 * Swipe-to-reveal, hand-rolled rather than `SwipeToDismissBox`, which is built
 * to throw a row away rather than park it open with buttons under it.
 *
 * Layered ON TOP of the tap, never instead of it: right reveals the action the
 * row's state is waiting on, left reveals Edit and Delete, and every one of
 * them is still in the bottom sheet a tap away. A gesture nothing advertises
 * must never be the only door to anything — the same reason long-press was
 * rejected for this screen.
 *
 * Drags are horizontal-only, so they don't fight the LazyColumn above them.
 * Offset and plate opacity are both read inside `graphicsLayer`/`offset`
 * lambdas, which defers them past composition — a row that recomposed on every
 * frame of a drag would stutter inside a list.
 */
@Composable
private fun SwipeRevealRow(
    rowId: String,
    openId: String?,
    onOpenChange: (String?) -> Unit,
    leading: SwipeAction?,
    trailing: List<SwipeAction>,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    // Two plates on one side of a phone screen have to give up some width or
    // the row itself disappears behind them.
    val plateDp: Dp = if (trailing.size > 1) 84.dp else 104.dp
    val platePx = with(density) { plateDp.toPx() }
    val trailingPx = platePx * trailing.size
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var rowWidth by remember { mutableStateOf(0f) }
    val tapSource = remember { MutableInteractionSource() }

    // Another row opening closes this one.
    LaunchedEffect(openId) {
        if (openId != rowId && offset.value != 0f) offset.animateTo(0f)
    }

    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .onSizeChanged { rowWidth = it.width.toFloat() }
    ) {
        Row(Modifier.matchParentSize()) {
            if (leading != null) {
                Plate(leading, plateDp,
                    Modifier.graphicsLayer { alpha = if (offset.value > 0f) 1f else 0f }) {
                    leading.run()
                    onOpenChange(null)
                    scope.launch { offset.animateTo(0f) }
                }
            }
            Spacer(Modifier.weight(1f))
            trailing.forEach { a ->
                Plate(a, plateDp,
                    Modifier.graphicsLayer { alpha = if (offset.value < 0f) 1f else 0f }) {
                    a.run()
                    onOpenChange(null)
                    scope.launch { offset.animateTo(0f) }
                }
            }
        }

        Box(
            Modifier
                .graphicsLayer { translationX = offset.value }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            var next = offset.value + delta
                            if (next > 0f && leading == null) next = 0f
                            if (next < 0f && trailing.isEmpty()) next = 0f
                            // Past the plates the row keeps moving but resists,
                            // so a full swipe reads as a deliberate extra shove.
                            if (next > platePx) next = platePx + (next - platePx) * 0.35f
                            if (next < -trailingPx) next = -trailingPx + (next + trailingPx) * 0.35f
                            offset.snapTo(next)
                        }
                    },
                    onDragStopped = {
                        val full = maxOf(rowWidth * 0.55f, platePx * 2f)
                        when {
                            // A full swipe fires the leading action outright.
                            // Never the trailing one: that is Delete, and a row
                            // should not vanish because a sleeve caught the screen.
                            leading != null && offset.value >= full -> {
                                leading.run()
                                onOpenChange(null)
                                offset.animateTo(0f)
                            }
                            leading != null && offset.value > platePx * 0.5f -> {
                                onOpenChange(rowId)
                                offset.animateTo(platePx)
                            }
                            trailing.isNotEmpty() && offset.value < -trailingPx * 0.5f -> {
                                onOpenChange(rowId)
                                offset.animateTo(-trailingPx)
                            }
                            else -> {
                                onOpenChange(null)
                                offset.animateTo(0f)
                            }
                        }
                    }
                )
        ) {
            content()
            // While a row is open a tap puts it away rather than falling
            // through to the sheet.
            if (openId == rowId) {
                Box(
                    Modifier.matchParentSize()
                        .clickable(interactionSource = tapSource, indication = null) {
                            onOpenChange(null)
                            scope.launch { offset.animateTo(0f) }
                        }
                )
            }
        }
    }
}

@Composable
private fun Plate(a: SwipeAction, width: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(a.tint.copy(alpha = 0.90f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(a.icon, contentDescription = a.title, tint = Color.White,
            modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(a.title, color = Color.White, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, lineHeight = 12.sp)
    }
}

@Composable
private fun MovementCard(m: HFPartMovement, today: LocalDate, onTap: () -> Unit) {
    val overdue = m.isOverdue(today)
    val days = m.daysRemaining(today)
    val accent = when {
        overdue -> HFColors.StatusRed
        m.coreOutstanding && days != null && days <= 7 -> HFColors.StatusOrange
        m.status == "received" || m.status == "refunded" -> HFColors.StatusGreen
        else -> HFColors.OnSurfaceMuted
    }

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(
                1.dp,
                if (overdue) HFColors.StatusRed.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onTap)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape)
                .background(
                    (if (m.isInbound) HFColors.StatusBlue else HFColors.StatusOrange)
                        .copy(alpha = 0.14f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (m.isInbound) Icons.Outlined.SouthWest else Icons.Outlined.NorthEast,
                contentDescription = if (m.isInbound) "Coming in" else "Going out",
                tint = if (m.isInbound) HFColors.StatusBlue else HFColors.StatusOrange,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                m.partNumber.ifBlank { m.description }.ifBlank { "Untitled part" },
                color = HFColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            val meta = listOfNotNull(
                m.description.takeIf { it.isNotBlank() && m.partNumber.isNotBlank() },
                m.kindLabel,
                m.vendorName.takeIf { it.isNotBlank() },
                m.coreDepositCents?.takeIf { it > 0 }?.let { "${money(it)} deposit" }
            )
            Row {
                m.planeTailNumber?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = HFColors.StatusBlue, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                }
                Text(meta.joinToString(" · "), color = HFColors.OnSurfaceMuted, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.clip(RoundedCornerShape(100.dp))
                .background(accent.copy(alpha = 0.30f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(m.chipLabel(today), color = accent, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, lineHeight = 11.sp)
        }
    }
}

@Composable
private fun SegmentChip(
    label: String, count: Int, selected: Boolean, urgent: Boolean, onClick: () -> Unit
) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(9.dp))
            .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (selected) HFColors.OnSurface else HFColors.OnSurfaceMuted,
            fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        if (count > 0) {
            Spacer(Modifier.width(7.dp))
            val accent = if (urgent) HFColors.StatusRed else HFColors.OnSurface
            Box(
                Modifier.clip(RoundedCornerShape(7.dp))
                    .background(accent.copy(alpha = if (urgent) 0.20f else 0.10f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("$count", color = if (urgent) HFColors.StatusRed else HFColors.OnSurfaceMuted,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, accent: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Text(value, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = HFColors.OnSurfaceMuted, fontSize = 10.sp)
    }
}

internal fun money(cents: Long): String = "$" + String.format("%,.2f", cents / 100.0)
