package com.hangarflow.app.i18n

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hangarflow.app.R

/**
 * Language selection, in one place so every entry point looks identical.
 *
 * Presented as a centred dialog rather than a dropdown. A dropdown anchors to
 * its trigger, and on a tablet that put the list in the far corner, detached
 * from the button that opened it. A language chooser is also the one screen a
 * lost user needs to read, so it gets the middle of the display.
 *
 * Every language appears in its OWN script with the English name beneath.
 * That has to cut both ways: someone stranded in Chinese cannot navigate a
 * list that only says "中文", and someone who only reads Chinese cannot use a
 * list that only says "Chinese".
 *
 * Selecting a language calls `Activity.recreate()` — Compose resolves strings
 * through the Activity's resources, so the configuration must be rebuilt for
 * a new catalogue to apply.
 */

private fun apply(context: Context, lang: HFLocale.Language) {
    HFLocale.save(context, lang)
    (context as? Activity)?.recreate()
}

@Composable
private fun label(lang: HFLocale.Language): String =
    if (lang == HFLocale.Language.SYSTEM) stringResource(R.string.language_system_default)
    else lang.nativeName

// ---------------------------------------------------------------- dialog

@Composable
fun HFLanguageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current = remember { HFLocale.saved(context) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF0B0B0D))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
                .padding(vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Language,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    stringResource(R.string.settings_language),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = Color.White.copy(alpha = 0.70f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Text(
                stringResource(R.string.settings_language_desc),
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
            )

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                HFLocale.Language.entries.forEach { lang ->
                    val selected = lang == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent
                            )
                            .clickable {
                                if (selected) onDismiss() else apply(context, lang)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                label(lang),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                            if (lang != HFLocale.Language.SYSTEM &&
                                lang.nativeName != lang.englishName
                            ) {
                                Text(
                                    lang.englishName,
                                    color = Color.White.copy(alpha = 0.42f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        if (selected) {
                            Text(
                                "✓",
                                color = Color(0xFF30D158),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- triggers

/**
 * Compact trigger for the sign-in screen.
 *
 * An icon plus the current language, not the word "Language" — the person who
 * most needs this button is the one who cannot read the current language, so
 * the affordance has to survive not being readable.
 */
@Composable
fun HFLanguageButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val current = remember { HFLocale.saved(context) }
    var open by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(100.dp))
            .clickable { open = true }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Language,
            contentDescription = stringResource(R.string.settings_language),
            tint = Color.White.copy(alpha = 0.70f),
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label(current),
            color = Color.White.copy(alpha = 0.80f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }

    if (open) HFLanguageDialog(onDismiss = { open = false })
}

/** Settings row. Opens the same dialog, so both places look identical. */
@Composable
fun HFLanguageSettingsRow() {
    val context = LocalContext.current
    val current = remember { HFLocale.saved(context) }
    var open by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { open = true }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.settings_language),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                stringResource(R.string.settings_language_desc),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label(current),
            color = Color.White.copy(alpha = 0.70f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }

    if (open) HFLanguageDialog(onDismiss = { open = false })
}
