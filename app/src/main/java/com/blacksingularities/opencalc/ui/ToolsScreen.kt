package com.blacksingularities.opencalc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencalc.ui.theme.Palette

data class Tool(val id: String, val title: String, val subtitle: String, val icon: ImageVector, val content: @Composable (AppViewModel) -> Unit)

val tools = listOf(
    Tool("equations", "Równania", "Kwadratowe, sześcienne, układy 2×2 i 3×3", Icons.Rounded.Functions) { EquationsTool(it) },
    Tool("solver", "Analiza funkcji", "f(x) = 0, pochodna, całka, wartości", Icons.Rounded.Search) { SolverTool(it) },
    Tool("stats", "Statystyka", "Średnia, mediana, odchylenie, regresja", Icons.Rounded.BarChart) { StatsTool(it) },
    Tool("matrix", "Macierze", "Wyznacznik, odwrotność, iloczyn, rząd", Icons.Rounded.GridOn) { MatrixTool(it) },
    Tool("bases", "Systemy liczbowe", "BIN, OCT, DEC, HEX i operacje bitowe", Icons.Rounded.Code) { BasesTool(it) },
    Tool("fractions", "Ułamki", "Działania na ułamkach, zamiana dziesiętnych", Icons.Rounded.PieChart) { FractionsTool(it) },
    Tool("numbers", "Teoria liczb", "Czynniki pierwsze, dzielniki, NWD, NWW, rzymskie", Icons.Rounded.Tag) { NumberTheoryTool(it) },
    Tool("percent", "Procenty", "Procent z liczby, zmiana, marża i rabat", Icons.Rounded.Percent) { PercentTool(it) },
    Tool("vat", "VAT i brutto/netto", "Stawki 23%, 8%, 5% i dowolna", Icons.Rounded.Receipt) { VatTool(it) },
    Tool("loan", "Kredyt", "Rata równa lub malejąca, harmonogram", Icons.Rounded.AccountBalance) { LoanTool(it) },
    Tool("savings", "Lokata i oszczędzanie", "Procent składany, wpłaty, podatek Belki", Icons.Rounded.Savings) { SavingsTool(it) },
    Tool("dates", "Daty", "Różnica dat, dodawanie dni, dzień tygodnia", Icons.Rounded.CalendarMonth) { DatesTool(it) },
    Tool("fuel", "Koszt podróży", "Spalanie, cena paliwa, podział na osoby", Icons.Rounded.LocalGasStation) { FuelTool(it) },
    Tool("tip", "Rachunek i napiwek", "Napiwek i podział rachunku", Icons.Rounded.Restaurant) { TipTool(it) },
    Tool("health", "BMI i zapotrzebowanie", "BMI, BMR, dzienne kalorie", Icons.Rounded.MonitorWeight) { HealthTool(it) },
    Tool("text", "Liczba słownie", "Kwota słownie po polsku, np. na przelew", Icons.Rounded.TextFields) { WordsTool(it) },
)

/**
 * Karta narzędzia: tytuł od samej góry, ikona w prawym górnym rogu, opis pod spodem.
 * Rogi idą za ustawieniem zaokrąglenia, tylko o kilka dp łagodniej niż przyciski.
 */
@Composable
private fun ToolCard(t: Tool, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LocalCorner.current + 4.dp))
            .background(Palette.Group)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 14.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                t.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                minLines = 2,
                maxLines = 2,
                // Miejsce na ikonę w rogu, żeby długi tytuł jej nie nachodził.
                modifier = Modifier.padding(end = 34.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(t.subtitle, color = Palette.TextDim, fontSize = 12.sp, lineHeight = 15.sp, minLines = 2, maxLines = 3)
        }
        Icon(
            t.icon,
            null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .clip(buttonShape())
                .background(Color.White.copy(alpha = 0.10f))
                .padding(5.dp),
        )
    }
}

@Composable
fun ToolsScreen(vm: AppViewModel) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val tool = tools.firstOrNull { it.id == open }
    if (tool == null) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(tools, key = { it.id }) { t -> ToolCard(t) { open = t.id } }
        }
    } else {
        BackHandler { open = null }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { open = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Wróć", tint = Color.White) }
                Spacer(Modifier.width(4.dp))
                Text(tool.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                tool.content(vm)
            }
        }
    }
}
