package com.paeffgen.plexcloud.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.paeffgen.plexcloud.R

/** Feste Farben des Entwurfs (dunkles Navy, technisch). Der Akzent ist frei wählbar. */
@Immutable
data class Plex(
    val accent: Color,
    val onAccent: Color,
    val ground: Color = Color(0xFF070B16),
    val surface: Color = Color(0xFF0F1526),
    val surfaceLow: Color = Color(0xFF0C1222),
    val nav: Color = Color(0xFF0A0F1E),
    val border: Color = Color(0xFF1E2944),
    val borderSoft: Color = Color(0xFF172038),
    val track: Color = Color(0xFF1A2340),
    val text: Color = Color(0xFFE8EEFF),
    val textSoft: Color = Color(0xFFC9D3EC),
    val muted: Color = Color(0xFF8C9AB8),
    val folder: Color = Color(0xFFF2B33D),
    val blue: Color = Color(0xFF5B8CFF),
    val violet: Color = Color(0xFFA98BFF),
    val danger: Color = Color(0xFFFF8A8A),
) {
    val accentSoft get() = accent.copy(alpha = 0.14f)
    val accentDim get() = accent.copy(alpha = 0.55f)
}

val LocalPlex = staticCompositionLocalOf { Plex(Color(0xFFFF7A2F), Color(0xFF0A0F1E)) }

private fun variable(id: Int, w: Int) = Font(id, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Inter = FontFamily(variable(R.font.inter, 400), variable(R.font.inter, 500), variable(R.font.inter, 600), variable(R.font.inter, 700))
val Grotesk = FontFamily(variable(R.font.space_grotesk, 500), variable(R.font.space_grotesk, 600), variable(R.font.space_grotesk, 700))
val Mono = FontFamily(variable(R.font.jetbrains_mono, 400), variable(R.font.jetbrains_mono, 500))

/** Zahlen, Pfade, Größen: Monospace */
val MonoSmall = TextStyle(fontFamily = Mono, fontSize = 11.sp, letterSpacing = 0.sp)
val MonoBody = TextStyle(fontFamily = Mono, fontSize = 13.sp)
val Label = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp)

@Composable
fun PlexTheme(accentArgb: Long, content: @Composable () -> Unit) {
    val accent = Color(accentArgb.toInt())
    // Text auf Akzentflächen: dunkel bei hellen Farben, sonst weiß
    val onAccent = if (accent.luminance() > 0.28f) Color(0xFF0A0F1E) else Color.White
    val p = Plex(accent, onAccent)
    val scheme = darkColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = p.accentSoft, onPrimaryContainer = p.text,
        secondary = p.blue, onSecondary = Color.White,
        background = p.ground, onBackground = p.text,
        surface = p.ground, onSurface = p.text,
        surfaceVariant = p.surface, onSurfaceVariant = p.muted,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surface, surfaceContainerLow = p.surfaceLow,
        outline = p.border, outlineVariant = p.borderSoft,
        error = p.danger,
    )
    val base = Typography()
    val type = Typography(
        headlineMedium = base.headlineMedium.copy(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 24.sp),
        titleLarge = base.titleLarge.copy(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleMedium = base.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        bodyLarge = base.bodyLarge.copy(fontFamily = Inter, fontSize = 14.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = Inter, fontSize = 13.sp),
        bodySmall = base.bodySmall.copy(fontFamily = Inter, fontSize = 12.sp),
        labelLarge = base.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        labelMedium = base.labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp),
        labelSmall = base.labelSmall.copy(fontFamily = Inter, fontSize = 11.sp),
    )
    CompositionLocalProvider(LocalPlex provides p) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}

/** Auswahl im Bildschirm „System“ */
val AccentPalette = listOf(
    "Orange" to 0xFFFF7A2FL,
    "Blau" to 0xFF5B8CFFL,
    "Türkis" to 0xFF3DD9C1L,
    "Lila" to 0xFFA98BFFL,
    "Grün" to 0xFF4CD964L,
    "Pink" to 0xFFFF5C8AL,
)
