package com.blacksingularities.opencalc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencalc.engine.Units
import com.blacksingularities.opencalc.engine.convert
import com.blacksingularities.opencalc.ui.theme.Palette

/**
 * Przelicznik: wpisujesz wartość w wybranej jednostce, a lista pokazuje ją od razu we wszystkich
 * jednostkach kategorii. Dotknięcie wiersza ustawia go jako źródło, przytrzymanie kopiuje wynik.
 */
@Composable
fun ConvertScreen(vm: AppViewModel) {
    var catId by rememberSaveable { mutableStateOf(vm.prefs.convertCategory) }
    val cat = Units.categories.firstOrNull { it.id == catId } ?: Units.categories.first()
    var fromId by rememberSaveable(cat.id) { mutableStateOf(cat.units.first { it.factor == 1.0 && it.toBase == null }.id) }
    var input by rememberSaveable { mutableStateOf("1") }
    val from = cat.units.firstOrNull { it.id == fromId } ?: cat.units.first()
    // Pole przyjmuje też wyrażenia: „12*2,5”, „√2”, „1/3”.
    val value = parseNum(input) ?: runCatching { vm.evalText(input.replace(',', '.')) }.getOrNull()
    val ctx = LocalContext.current
    val style = vm.settings.style.copy(digits = 10)

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        ChipRow(Units.categories, cat, { it.name }, {
            catId = it.id
            vm.prefs.convertCategory = it.id
        }, Modifier.padding(vertical = 8.dp))
        Section(cat.name) {
            NumField(input, { input = it }, "Wartość", Modifier.fillMaxWidth(), suffix = from.symbol, keyboard = androidx.compose.ui.text.input.KeyboardType.Text)
            if (value == null && input.isNotBlank()) ErrorText("Niepoprawna liczba lub wyrażenie")
            Hint("${from.name} · dotknij jednostki poniżej, by przeliczać z niej; przytrzymaj, by skopiować wynik")
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(cat.units, key = { it.id }) { u ->
                val result = value?.let { convert(it, from, u) }
                val text = result?.let { com.blacksingularities.opencalc.engine.Fmt.number(it, style) } ?: "—"
                val selected = u.id == from.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) Color.White else Palette.Group)
                        .clickable {
                            if (result != null) input = com.blacksingularities.opencalc.engine.Fmt.plain(result)
                            fromId = u.id
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(u.name, color = if (selected) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(u.symbol, color = if (selected) Color.Black.copy(alpha = 0.6f) else Palette.TextFaint, fontSize = 12.sp)
                    }
                    Text(
                        text,
                        color = if (selected) Color.Black else Color.White,
                        fontSize = 17.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable {
                            copyText(ctx, text.replace(" ", ""))
                            vm.message = "Skopiowano: $text ${u.symbol}"
                        },
                    )
                }
            }
        }
    }
}
