package com.hangarflow.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.ui.auth.LoginScreen
import com.hangarflow.app.ui.home.HomeDestination
import com.hangarflow.app.ui.home.HomeHub
import com.hangarflow.app.ui.hubs.HubSheetHost
import com.hangarflow.app.ui.theme.HFColors

/**
 * Auth gate. Spinner during session restore. Login screen when signed
 * out. Home hub (iOS-style) when signed in. Feature sheets overlay on
 * top when a card is tapped, matching the iOS presentation model.
 */
@Composable
fun RootScreen() {
    val state by AuthManager.state.collectAsState()
    var openHub by remember { mutableStateOf<HomeDestination?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HFColors.Background)
    ) {
        when {
            state.loading && !state.isSignedIn ->
                CircularProgressIndicator(
                    color = HFColors.OnSurface,
                    modifier = Modifier.align(Alignment.Center)
                )

            state.isSignedIn -> {
                HomeHub(onOpenHub = { openHub = it })
                openHub?.let { destination ->
                    HubSheetHost(
                        destination = destination,
                        onDismiss = { openHub = null }
                    )
                }
            }

            else -> LoginScreen()
        }
    }
}
