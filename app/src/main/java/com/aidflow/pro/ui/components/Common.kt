package com.aidflow.pro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aidflow.pro.translate.Language

@Composable
fun LanguagePicker(
    label: String,
    selected: Language,
    onSelect: (Language) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Box {
            AssistChip(
                onClick = { expanded = true },
                label = { Text(selected.displayName) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Language.values().forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang.displayName) },
                        onClick = { onSelect(lang); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
fun OfflineBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "100% offline",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
fun BusyOverlay(visible: Boolean, label: String = "") {
    if (!visible) return
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Row(
                Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(strokeWidth = 3.dp)
                if (label.isNotBlank()) {
                    Spacer(Modifier.width(16.dp))
                    Text(label)
                }
            }
        }
    }
}
