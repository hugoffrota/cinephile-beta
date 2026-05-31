package com.thalos.cinephile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RichBlack = Color(0xFF0A0A0F)
val EerieBlack = Color(0xFF13131F)
val Gunmetal = Color(0xFF1A1A2E)
val UltraViolet = Color(0xFF7C3AED)
val ElectricViolet = Color(0xFF8B5CF6)
val Amethyst = Color(0xFFA78BFA)
val Platinum = Color(0xFFE2E8F0)
val CadetGrey = Color(0xFF94A3B8)
val Emerald = Color(0xFF10B981)
val Cinnabar = Color(0xFFEF4444)
val Rose = Color(0xFFF43F5E)
val Amber = Color(0xFFF59E0B)

private val DarkColors = darkColorScheme(
    primary = UltraViolet,
    onPrimary = Color.White,
    primaryContainer = UltraViolet.copy(alpha = 0.15f),
    onPrimaryContainer = Amethyst,
    secondary = ElectricViolet,
    onSecondary = Color.White,
    background = RichBlack,
    onBackground = Platinum,
    surface = EerieBlack,
    onSurface = Platinum,
    surfaceVariant = Gunmetal,
    onSurfaceVariant = CadetGrey,
    outline = UltraViolet.copy(alpha = 0.3f),
    error = Rose,
    onError = Color.White
)

@Composable
fun CinephileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = CinephileTypography,
        content = content
    )
}
