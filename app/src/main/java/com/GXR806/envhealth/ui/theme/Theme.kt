package com.GXR806.envhealth.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = HealthMint, onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005047), onPrimaryContainer = Color(0xFF94F5E4),
    secondary = HealthSky, onSecondary = Color(0xFF003258),
    background = Color(0xFF101C1B), surface = Color(0xFF101C1B),
    onBackground = Color(0xFFDCE5E1), onSurface = Color(0xFFDCE5E1),
    surfaceVariant = Color(0xFF394B46), onSurfaceVariant = Color(0xFFBECEC7)
)
private val LightColorScheme = lightColorScheme(
    primary = HealthTeal, onPrimary = Color.White,
    primaryContainer = Color(0xFF95F5E4), onPrimaryContainer = Color(0xFF00201B),
    secondary = HealthBlue, onSecondary = Color.White,
    background = Color(0xFFF4FAF7), surface = Color(0xFFF4FAF7),
    onBackground = Color(0xFF152B25), onSurface = Color(0xFF152B25),
    surfaceVariant = Color(0xFFDAE8E1), onSurfaceVariant = Color(0xFF3E4D47)
)

@Composable
fun 环境健康Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = HealthShapes,
        content = content
    )
}
