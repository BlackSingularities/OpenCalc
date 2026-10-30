package com.blacksingularities.opencalc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    // Czarno-biały interfejs jak w pozostałych aplikacjach Open*: akcent = biel, czerwień dla błędów.
    val Ink = Color(0xFF000000)
    val Panel = Color(0xFF000000)
    val PanelHigh = Color(0xFF1C1C1C)
    // Powierzchnie jak w OpenPDF: biel o niskim kryciu na czystej czerni.
    val Key = Color(0x12FFFFFF)
    val KeyFn = Color(0x0DFFFFFF)
    val KeyOp = Color(0x24FFFFFF)
    val Group = Color(0x0FFFFFFF)
    val Line = Color(0x26FFFFFF)
    val TextDim = Color(0xB3FFFFFF)
    val TextFaint = Color(0x66FFFFFF)
    val Error = Color(0xFFFF5A5F)

    /** Kolory wykresów – czytelne na czarnym tle i różne dla daltonistów (kolejność ma znaczenie). */
    val Graph = listOf(
        Color(0xFF4FC3F7), Color(0xFFFFB74D), Color(0xFF81C784), Color(0xFFF06292),
        Color(0xFFBA68C8), Color(0xFFFFF176), Color(0xFF4DB6AC), Color(0xFFE0E0E0),
    )
}

private val scheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Palette.Ink,
    secondary = Color(0xFFE0E0E0),
    onSecondary = Palette.Ink,
    background = Palette.Ink,
    onBackground = Color.White,
    surface = Palette.Panel,
    onSurface = Color.White,
    surfaceVariant = Palette.PanelHigh,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainerLowest = Palette.Panel,
    surfaceContainerLow = Palette.Panel,
    surfaceContainer = Palette.PanelHigh,
    surfaceContainerHigh = Palette.PanelHigh,
    surfaceContainerHighest = Palette.KeyFn,
    secondaryContainer = Palette.KeyOp,
    onSecondaryContainer = Color.White,
    outline = Color(0x40FFFFFF),
    outlineVariant = Palette.Line,
    error = Palette.Error,
)

@Composable
fun OpenCalcTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
