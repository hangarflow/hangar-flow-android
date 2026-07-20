package com.hangarflow.app.ui.hubs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hangarflow.app.data.SharedStore
import com.hangarflow.app.data.model.HFPlane
import com.hangarflow.app.data.model.HFSquawk
import com.hangarflow.app.ui.theme.HFColors
import kotlinx.coroutines.launch

/**
 * Full-screen sheet for editing an existing squawk from Android — plane,
 * title, category, notes, and the corrective action (what was done to fix
 * it). Mirrors the Desktop EditSquawkDialog: saving a non-blank corrective
 * action stamps who/when (handled in SharedStore.updateSquawk).
 */
@Composable
fun EditSquawkSheet(squawk: HFSquawk, onDismiss: () -> Unit) {
    val shopState by SharedStore.state.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedPlane by remember {
        mutableStateOf(shopState.planes.firstOrNull { it.tailNumber == squawk.planeTailNumber || it.id == squawk.planeId })
    }
    var planeMenuExpanded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(squawk.title) }
    var category by remember { mutableStateOf(squawk.category) }
    var notes by remember { mutableStateOf(squawk.notes) }
    var corrective by remember { mutableStateOf(squawk.correctiveAction) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    val canSave = title.isNotBlank() && !isSaving

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HFColors.Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Edit Squawk", color = HFColors.OnSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Update the discrepancy or record what was done to fix it.",
                    color = HFColors.OnSurface.copy(alpha = 0.68f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(HFColors.OnSurface.copy(alpha = 0.10f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = HFColors.OnSurface, modifier = Modifier.size(16.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            EditSectionLabel("Plane")
            Box {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(HFColors.OnSurface.copy(alpha = 0.06f))
                        .border(1.dp, HFColors.OnSurface.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                        .clickable { planeMenuExpanded = true }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedPlane?.let { "${it.tailNumber} — ${it.displayName.ifBlank { "Unnamed" }}" }
                            ?: squawk.planeTailNumber.ifBlank { "Pick a plane" },
                        color = HFColors.OnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                DropdownMenu(
                    expanded = planeMenuExpanded,
                    onDismissRequest = { planeMenuExpanded = false }
                ) {
                    shopState.planes.forEach { plane ->
                        DropdownMenuItem(
                            text = { Text("${plane.tailNumber} — ${plane.displayName.ifBlank { "Unnamed" }}") },
                            onClick = {
                                selectedPlane = plane
                                planeMenuExpanded = false
                            }
                        )
                    }
                }
            }

            EditSectionLabel("Title")
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = editFieldColors()
            )

            EditSectionLabel("Category")
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = editFieldColors()
            )

            EditSectionLabel("Notes")
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                colors = editFieldColors()
            )

            HorizontalDivider(color = HFColors.OnSurface.copy(alpha = 0.10f))

            Text(
                "CORRECTIVE ACTION",
                color = HFColors.StatusGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            OutlinedTextField(
                value = corrective,
                onValueChange = { corrective = it },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                placeholder = {
                    Text(
                        "What was done to fix it — e.g. Bled brakes, replaced O-ring MS28775-012, ops check good",
                        color = HFColors.OnSurface.copy(alpha = 0.4f),
                        fontSize = 13.sp
                    )
                },
                colors = editFieldColors()
            )

            saveError?.let { msg ->
                Text(msg, color = HFColors.StatusRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.size(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canSave) HFColors.BrandWhite else HFColors.OnSurface.copy(alpha = 0.15f))
                    .clickable(enabled = canSave) {
                        isSaving = true
                        saveError = null
                        val plane = selectedPlane
                        scope.launch {
                            when (val r = SharedStore.updateSquawk(
                                squawkId = squawk.id,
                                title = title,
                                planeId = plane?.id ?: squawk.planeId,
                                planeTailNumber = plane?.tailNumber ?: squawk.planeTailNumber,
                                category = category.ifBlank { "general" },
                                notes = notes,
                                correctiveAction = corrective
                            )) {
                                SharedStore.CreateResult.Success -> onDismiss()
                                is SharedStore.CreateResult.Error -> {
                                    saveError = r.message
                                    isSaving = false
                                }
                            }
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = HFColors.BrandInk, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text(
                        "Save Changes",
                        color = if (canSave) HFColors.BrandInk else HFColors.OnSurface.copy(alpha = 0.4f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.size(40.dp))
        }
    }
}

@Composable
private fun EditSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = HFColors.OnSurface.copy(alpha = 0.55f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
    unfocusedContainerColor = HFColors.OnSurface.copy(alpha = 0.04f),
    focusedBorderColor = HFColors.OnSurface.copy(alpha = 0.25f),
    unfocusedBorderColor = HFColors.OnSurface.copy(alpha = 0.10f),
    focusedTextColor = HFColors.OnSurface,
    unfocusedTextColor = HFColors.OnSurface,
    cursorColor = HFColors.OnSurface
)
