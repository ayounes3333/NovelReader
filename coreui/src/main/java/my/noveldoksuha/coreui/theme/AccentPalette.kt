package my.noveldoksuha.coreui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Default brand accent — Material You Indigo/Violet. */
val DefaultAccent = Color(0xFF6650A4)

/** Accent colors offered in the theme settings. */
val AccentPresets: List<Color> = listOf(
    DefaultAccent,          // Indigo
    Color(0xFF3F51B5),      // Blue
    Color(0xFF0288D1),      // Sky
    Color(0xFF00897B),      // Teal
    Color(0xFF43A047),      // Green
    Color(0xFFF9A825),      // Amber
    Color(0xFFEF6C00),      // Orange
    Color(0xFFD81B60),      // Pink
    Color(0xFFC62828),      // Red
    Color(0xFF6D4C41),      // Brown
    Color(0xFF546E7A),      // Blue grey
)

/**
 * Tonal ramp derived from a single accent color. Tone numbers follow the
 * Material naming (10 = darkest, 95 = lightest) and roughly match the
 * original indigo ramp when [accent] is [DefaultAccent].
 */
@Immutable
data class AccentPalette(val accent: Color) {
    val t10: Color = accent.mix(Color.Black, 0.35f)
    val t20: Color = accent.mix(Color.Black, 0.55f)
    val t30: Color = accent.mix(Color.Black, 0.78f)
    val t40: Color = accent
    val t50: Color = accent.mix(Color.White, 0.85f)
    val t60: Color = accent.mix(Color.White, 0.68f)
    val t70: Color = accent.mix(Color.White, 0.50f)
    val t80: Color = accent.mix(Color.White, 0.34f)
    val t90: Color = accent.mix(Color.White, 0.16f)
    val t95: Color = accent.mix(Color.White, 0.08f)

    /** Readable content color placed on top of [accent]. */
    val onAccent: Color = if (accent.luminance() > 0.45f) Color(0xFF1B1B1B) else Color.White

    /** Dark surfaces subtly tinted with the accent (used by dark/black themes). */
    val darkTint1: Color = Grey900.mix(accent, 0.90f)
    val darkTint2: Color = Grey900.mix(accent, 0.82f)
    val darkTint3: Color = Grey900.mix(accent, 0.74f)
    val darkTintLow: Color = Grey900.mix(accent, 0.95f)
    val blackTint1: Color = Grey1000.mix(accent, 0.92f)
    val blackTint2: Color = Grey1000.mix(accent, 0.86f)
    val blackTint3: Color = Grey1000.mix(accent, 0.80f)
    val blackTintLow: Color = Grey1000.mix(accent, 0.96f)
}
