package com.aidflow.pro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = BrandAmber,
    background = SurfaceLight,
    surface = CardLight,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEDF2F8),
    onSurfaceVariant = MutedLight,
)

private val DarkColors = darkColorScheme(
    primary = BrandBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = BrandAmber,
    background = SurfaceDark,
    surface = CardDark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1F2A38),
    onSurfaceVariant = MutedDark,
)

@Composable
fun AidFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AidFlowTypography,
        content = content,
    )
}
