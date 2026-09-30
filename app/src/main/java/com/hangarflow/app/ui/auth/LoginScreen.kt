package com.hangarflow.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.hangarflow.app.i18n.HFLanguageButton
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.R
import com.hangarflow.app.auth.AuthManager
import com.hangarflow.app.ui.theme.HFColors
import com.hangarflow.app.ui.theme.HFAppearanceButton

/**
 * Port of the macOS `LoginView` look: gradient + radial glow background,
 * brand header (HF logo + wordmark), and a single glassy panel with
 * underline-style inputs, a show/hide pill, and a black-on-white submit
 * button ending in an arrow.
 */
@Composable
fun LoginScreen() {
    val state by AuthManager.state.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val canSubmit = email.trim().isNotEmpty() &&
        password.trim().isNotEmpty() &&
        !state.loading

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HFColors.Background)
            .windowInsetsPadding(WindowInsets.ime)
    ) {
        LoginBackground()

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 40.dp)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(26.dp)
            ) {
                BrandHeader()
                AuthPanel(
                    email = email,
                    onEmailChange = { email = it },
                    password = password,
                    onPasswordChange = { password = it },
                    showPassword = showPassword,
                    onToggleShow = { showPassword = !showPassword },
                    canSubmit = canSubmit,
                    isBusy = state.loading,
                    error = state.error,
                    onSubmit = { AuthManager.signIn(email.trim(), password) }
                )
            }
        }

        // Language and appearance BEFORE sign-in. The person who most needs
        // these is the one who cannot read the screen well enough to sign in
        // and find Settings — whether that is the language or the glare on a
        // sunlit ramp — so both have to be reachable from here.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HFLanguageButton()
            HFAppearanceButton()
        }
    }
}

// ---------- Background ----------

@Composable
private fun LoginBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        HFColors.Background,
                        HFColors.BackgroundAlt,
                        HFColors.Background
                    )
                )
            )
    ) {
        // Soft white glow top-left — matches the macOS radial gradient.
        Box(
            modifier = Modifier
                .size(520.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            HFColors.fill(0.09f),
                            HFColors.fill(0.03f),
                            Color.Transparent
                        )
                    )
                )
                .blur(24.dp)
        )
        // Cool bottom-right glow for depth.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(420.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            HFColors.Glow.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
                .blur(30.dp)
        )
    }
}

// ---------- Brand lockup ----------

@Composable
private fun BrandHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(id = R.drawable.hf_brand_logo),
            contentDescription = "Hangar Flow",
            // The asset is white pixels on transparency, so on a white page it
            // is simply not there. Tinting costs nothing in dark mode, where
            // OnSurface already is white.
            colorFilter = ColorFilter.tint(HFColors.OnSurface),
            modifier = Modifier.size(68.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Hangar Flow",
            color = HFColors.OnSurface,
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ---------- Glass auth panel ----------

@Composable
private fun AuthPanel(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onToggleShow: () -> Unit,
    canSubmit: Boolean,
    isBusy: Boolean,
    error: String?,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(HFColors.Background.copy(alpha = 0.46f))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        HFColors.fill(0.10f),
                        HFColors.fill(0.05f)
                    )
                )
            )
            .border(1.dp, HFColors.stroke(0.11f), RoundedCornerShape(28.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            stringResource(R.string.login_title),
            color = HFColors.OnSurface,
            fontSize = 25.sp,
            fontWeight = FontWeight.SemiBold
        )

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            UnderlineField(
                label = stringResource(R.string.login_email),
                icon = Icons.Outlined.Email,
                value = email,
                onChange = onEmailChange,
                placeholder = "you@shop.com",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )

            UnderlineField(
                label = stringResource(R.string.login_password),
                icon = Icons.Outlined.Lock,
                value = password,
                onChange = onPasswordChange,
                placeholder = "••••••••",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
                onImeAction = { if (canSubmit) onSubmit() },
                visualTransformation =
                    if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                trailing = {
                    ShowHidePill(showing = showPassword, onClick = onToggleShow)
                }
            )
        }

        if (!error.isNullOrBlank()) ErrorCard(error)

        SubmitButton(enabled = canSubmit, isBusy = isBusy, onClick = onSubmit)

        SignUpHint()
    }
}

@Composable
private fun SignUpHint() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            stringResource(R.string.login_no_account),
            color = HFColors.ink(0.60f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            stringResource(R.string.login_create_at),
            color = HFColors.OnSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable {
                runCatching {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://hangarflow.com")
                    )
                    context.startActivity(intent)
                }
            }
        )
    }
}

// ---------- Inputs ----------

@Composable
private fun UnderlineField(
    label: String,
    icon: ImageVector,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label.uppercase(),
            color = HFColors.ink(0.48f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.0.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HFColors.ink(0.50f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.size(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        color = HFColors.ink(0.30f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    visualTransformation = visualTransformation,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = imeAction
                    ),
                    textStyle = TextStyle(
                        color = HFColors.OnSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(HFColors.OnSurface),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (trailing != null) {
                Spacer(Modifier.size(8.dp))
                trailing()
            }
        }
        // Subtle underline — the macOS design uses a 1px divider, not a
        // boxed field, to keep the panel feeling light.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HFColors.fill(0.14f))
        )
    }
}

@Composable
private fun ShowHidePill(showing: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(HFColors.fill(0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            if (showing) stringResource(R.string.login_hide) else stringResource(R.string.login_show),
            color = HFColors.ink(0.72f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ---------- Error + submit ----------

@Composable
private fun ErrorCard(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HFColors.StatusRed.copy(alpha = 0.10f))
            .border(1.dp, HFColors.StatusRed.copy(alpha = 0.24f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = HFColors.StatusRed.copy(alpha = 0.92f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.size(10.dp))
        Text(
            message,
            color = HFColors.ink(0.82f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SubmitButton(enabled: Boolean, isBusy: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) HFColors.BrandWhite else HFColors.BrandWhite.copy(alpha = 0.46f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                color = HFColors.BrandInk,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.size(10.dp))
        }
        Text(
            stringResource(R.string.login_title),
            color = HFColors.BrandInk,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = HFColors.BrandInk,
            modifier = Modifier.size(16.dp)
        )
    }
}
