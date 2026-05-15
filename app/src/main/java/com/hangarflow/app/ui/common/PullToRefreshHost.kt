package com.hangarflow.app.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hangarflow.app.data.SharedStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Wraps any scrollable hub content with swipe-down-to-refresh wired to
 * [SharedStore.refresh]. Keeps the spinner visible for a tiny beat after
 * the pull returns so a successful re-sync still feels like "something
 * happened" instead of a no-op flash.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HFPullToRefreshHost(content: @Composable () -> Unit) {
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            scope.launch {
                SharedStore.refresh()
                delay(600)
                isRefreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        content()
    }
}
