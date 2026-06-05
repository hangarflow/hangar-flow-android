package com.hangarflow.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hangarflow.app.data.cloud.HFCloudSyncService
import com.hangarflow.app.ui.theme.HFColors

/**
 * Full-screen photo viewer with swipe-between-photos + pinch-to-zoom.
 * Photos are loaded from signed Supabase URLs via Coil.
 */
@Composable
fun FullScreenPhotoViewer(
    photoPaths: List<String>,
    initialIndex: Int,
    onClose: () -> Unit,
    /**
     * How to mint a signed URL for a given storage path. Defaults to the
     * squawk-photos bucket; part-location photos pass their own signer so
     * the same viewer serves both private buckets.
     */
    signedUrlFor: suspend (String) -> String = { path ->
        HFCloudSyncService().signedSquawkPhotoURL(path)
    }
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (photoPaths.size - 1).coerceAtLeast(0)),
        pageCount = { photoPaths.size }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HFColors.Background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ZoomablePhoto(
                path = photoPaths[page],
                signedUrlFor = signedUrlFor
            )
        }

        // Top bar: close + counter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(HFColors.OnSurface.copy(alpha = 0.14f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    tint = HFColors.OnSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (photoPaths.size > 1) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(HFColors.OnSurface.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        "${pagerState.currentPage + 1} / ${photoPaths.size}",
                        color = HFColors.OnSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(path: String, signedUrlFor: suspend (String) -> String) {
    var signedUrl by remember(path) { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(path) {
        signedUrl = runCatching { signedUrlFor(path) }.getOrNull()
    }

    var scale by remember(path) { mutableStateOf(1f) }
    var offsetX by remember(path) { mutableStateOf(0f) }
    var offsetY by remember(path) { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(path) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.count { it.pressed }
                        if (pointers >= 2) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 8f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (signedUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(signedUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )
        } else {
            CircularProgressIndicator(
                color = HFColors.OnSurface,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
