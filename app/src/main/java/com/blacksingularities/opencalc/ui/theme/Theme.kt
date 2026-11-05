package com.blacksingularities.opencalc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Motyw z ustawień: jak w systemie, zawsze ciemny albo zawsze jasny. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/**
 * Kolory interfejsu. Ciemny motyw to biel na czystej czerni (jak w pozostałych aplikacjach Open*),
 * jasny – jego lustrzane odbicie: czerń na bieli. Akcent zawsze ma kolor tekstu ([Fg]), a tekst na
 * akcencie – kolor tła ([Bg]), więc ekrany nie muszą znać bieżącego motywu.
 */
object Palette {
    var dark by mutableStateOf(true)

    private fun pick(d: Long, l: Long) = Color(if (dark) d else l)

    /** Kolor tekstu i akcentu (biel w ciemnym motywie, czerń w jasnym). */
    val Fg get() = pick(0xFFFFFFFF, 0xFF000000)
    /** Kolor tła i tekstu na akcencie. */
    val Bg get() = pick(0xFF000000, 0xFFFFFFFF)
    val Ink get() = Bg
    val Panel get() = Bg
    val PanelHigh get() = pick(0xFF1C1C1C, 0xFFF1F1F3)
    // Powierzchnie jak w OpenPDF: kolor tekstu o niskim kryciu na tle.
    val Key get() = pick(0x12FFFFFF, 0x0F000000)
    val KeyFn get() = pick(0x0DFFFFFF, 0x09000000)
    val KeyOp get() = pick(0x24FFFFFF, 0x1C000000)
    val Group get() = pick(0x0FFFFFFF, 0x0A000000)
    val Line get() = pick(0x26FFFFFF, 0x24000000)
    val TextDim get() = pick(0xC2FFFFFF, 0xC2000000)
    // Co najmniej 4,5:1 kontrastu z tłem (WCAG AA) – także dla drobnych podpisów.
    val TextFaint get() = pick(0x8FFFFFFF, 0x94000000)
    val Error get() = pick(0xFFFF5A5F, 0xFFC62828)

    /** Kolory wykresów – czytelne na tle motywu i różne dla daltonistów (kolejność ma znaczenie). */
    val Graph: List<Color>
        get() = if (dark) listOf(
            Color(0xFF4FC3F7), Color(0xFFFFB74D), Color(0xFF81C784), Color(0xFFF06292),
            Color(0xFFBA68C8), Color(0xFFFFF176), Color(0xFF4DB6AC), Color(0xFFE0E0E0),
        ) else listOf(
            Color(0xFF0277BD), Color(0xFFE65100), Color(0xFF2E7D32), Color(0xFFC2185B),
            Color(0xFF7B1FA2), Color(0xFF8D6E00), Color(0xFF00796B), Color(0xFF424242),
        )
}

private fun scheme(dark: Boolean) = if (dark) darkColorScheme(
    primary = Palette.Fg,
    onPrimary = Palette.Bg,
    secondary = Color(0xFFE0E0E0),
    onSecondary = Palette.Bg,
    background = Palette.Bg,
    onBackground = Palette.Fg,
    surface = Palette.Panel,
    onSurface = Palette.Fg,
    surfaceVariant = Palette.PanelHigh,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainerLowest = Palette.Panel,
    surfaceContainerLow = Palette.Panel,
    surfaceContainer = Palette.PanelHigh,
    surfaceContainerHigh = Palette.PanelHigh,
    surfaceContainerHighest = Palette.KeyFn,
    secondaryContainer = Palette.KeyOp,
    onSecondaryContainer = Palette.Fg,
    outline = Color(0x40FFFFFF),
    outlineVariant = Palette.Line,
    error = Palette.Error,
) else lightColorScheme(
    primary = Palette.Fg,
    onPrimary = Palette.Bg,
    secondary = Color(0xFF303030),
    onSecondary = Palette.Bg,
    background = Palette.Bg,
    onBackground = Palette.Fg,
    surface = Palette.Panel,
    onSurface = Palette.Fg,
    surfaceVariant = Palette.PanelHigh,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainerLowest = Palette.Panel,
    surfaceContainerLow = Palette.Panel,
    surfaceContainer = Palette.PanelHigh,
    surfaceContainerHigh = Palette.PanelHigh,
    surfaceContainerHighest = Color(0xFFE6E6E8),
    secondaryContainer = Color(0xFFE3E3E3),
    onSecondaryContainer = Palette.Fg,
    outline = Color(0x52000000),
    outlineVariant = Palette.Line,
    error = Palette.Error,
)

@Composable
fun OpenCalcTheme(dark: Boolean, content: @Composable () -> Unit) {
    // Paleta jest stanem Compose – zmiana motywu przerysowuje wszystkie ekrany.
    if (Palette.dark != dark) Palette.dark = dark
    MaterialTheme(colorScheme = scheme(dark), typography = Typography(), content = content)
}
