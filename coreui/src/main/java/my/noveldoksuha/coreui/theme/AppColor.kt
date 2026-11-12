package my.noveldoksuha.coreui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AppColor(
    val accent: Color,
    val onAccent: Color,
    val tabSurface: Color,
    val bookSurface: Color,
    val checkboxPositive: Color,
    val checkboxNegative: Color,
    val checkboxNeutral: Color,
    val tintedSurface: Color,
    val tintedSelectedSurface: Color,
)

fun lightAppColor(palette: AccentPalette) = AppColor(
    accent = palette.accent,
    onAccent = palette.onAccent,
    tabSurface = palette.t90,
    bookSurface = palette.t95,
    checkboxPositive = Success500,
    checkboxNegative = Error500,
    checkboxNeutral = Grey900,
    tintedSurface = Grey25.mix(palette.accent, 0.65f),
    tintedSelectedSurface = Grey25.mix(palette.accent, 0.75f),
)

fun darkAppColor(palette: AccentPalette) = AppColor(
    accent = palette.accent,
    onAccent = palette.onAccent,
    tabSurface = palette.darkTint2,
    bookSurface = Grey800,
    checkboxPositive = Success500,
    checkboxNegative = Error500,
    checkboxNeutral = Grey900,
    tintedSurface = Grey900.mix(palette.accent, 0.65f),
    tintedSelectedSurface = Grey900.mix(palette.accent, 0.75f),
)

fun blackAppColor(palette: AccentPalette) = AppColor(
    accent = palette.accent,
    onAccent = palette.onAccent,
    tabSurface = palette.t20,
    bookSurface = Grey900,
    checkboxPositive = Success500,
    checkboxNegative = Error500,
    checkboxNeutral = Grey900,
    tintedSurface = Grey1000.mix(palette.accent, 0.65f),
    tintedSelectedSurface = Grey1000.mix(palette.accent, 0.75f),
)

val light_appColor = lightAppColor(AccentPalette(DefaultAccent))
val dark_appColor = darkAppColor(AccentPalette(DefaultAccent))
val black_appColor = blackAppColor(AccentPalette(DefaultAccent))

val LocalAppColor = compositionLocalOf { light_appColor }

@Suppress("UnusedReceiverParameter")
val MaterialTheme.colorApp: AppColor
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColor.current
