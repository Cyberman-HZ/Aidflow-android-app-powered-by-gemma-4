package com.aidflow.pro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aidflow.pro.R
import com.aidflow.pro.ui.components.OfflineBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onTranslate: () -> Unit,
    onFamilyIntake: () -> Unit,
    onIdentifyItems: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(title = {
            Column {
                Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }, actions = { OfflineBadge(Modifier.padding(end = 12.dp)) })
    }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FeatureCard(
                icon = Icons.Filled.PersonAdd,
                title = "Family intake",
                subtitle = "Speak or photograph a family record → Excel for the AidFlow web app",
                onClick = onFamilyIntake,
            )
            FeatureCard(
                icon = Icons.Filled.Inventory2,
                title = "Identify items",
                subtitle = "Photograph relief supplies → categorized inventory spreadsheet",
                onClick = onIdentifyItems,
            )
            FeatureCard(
                icon = Icons.Filled.DocumentScanner,
                title = stringResource(R.string.home_scan_title),
                subtitle = stringResource(R.string.home_scan_subtitle),
                onClick = onScan,
            )
            FeatureCard(
                icon = Icons.Filled.Mic,
                title = stringResource(R.string.home_translate_title),
                subtitle = stringResource(R.string.home_translate_subtitle),
                onClick = onTranslate,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Powered by Gemma 4 E2B running on your device — no data leaves the phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
