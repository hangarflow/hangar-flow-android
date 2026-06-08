package com.hangarflow.app.ui.hubs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.model.HFAuditEvent
import com.hangarflow.app.ui.theme.HFColors

/** The paper trail — append-only log of who did what, when. Header is
 *  provided by HubSheetHost; this renders the list. Visible to everyone. */
@Composable
fun ActivityLogHub() {
    var events by remember { mutableStateOf<List<HFAuditEvent>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        events = SharedStore.fetchAuditEvents(300)
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        when {
            loading -> Text("Loading…", color = HFColors.OnSurfaceMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            events.isEmpty() -> Text(
                "No activity recorded yet. New work logs, squawks, plane adds, parts requests, and clock-outs show up here.",
                color = HFColors.OnSurfaceMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(events, key = { it.id }) { ev ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(HFColors.OnSurface.copy(alpha = 0.04f))
                            .padding(12.dp)
                    ) {
                        Text(ev.summary, color = HFColors.OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "${ev.actorName.ifBlank { "Unknown" }} · ${auditRelative(ev.createdAt)}",
                            color = HFColors.OnSurfaceMuted, fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

private fun auditRelative(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val then = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return ""
    val secs = java.time.Duration.between(then, java.time.Instant.now()).seconds
    return when {
        secs < 60 -> "just now"
        secs < 3600 -> "${secs / 60}m ago"
        secs < 86400 -> "${secs / 3600}h ago"
        secs < 604800 -> "${secs / 86400}d ago"
        else -> java.time.format.DateTimeFormatter.ofPattern("MMM d")
            .withZone(java.time.ZoneId.systemDefault()).format(then)
    }
}
