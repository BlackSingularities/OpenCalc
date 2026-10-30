package com.blacksingularities.opencalc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencalc.ui.theme.Palette
import kotlinx.coroutines.delay

val LocalVm = staticCompositionLocalOf<AppViewModel> { error("Brak AppViewModel") }

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Kalkulator", Icons.Rounded.Calculate),
    Tab("Wykresy", Icons.Rounded.ShowChart),
    Tab("Przeliczniki", Icons.Rounded.SwapHoriz),
    Tab("Narzędzia", Icons.Rounded.Handyman),
    Tab("Więcej", Icons.Rounded.MoreHoriz),
)

@Composable
fun AppRoot(vm: AppViewModel) {
    CompositionLocalProvider(LocalVm provides vm) {
        BackHandler(enabled = vm.tab != 0) { vm.selectTab(0) }
        Box(Modifier.fillMaxSize().background(Palette.Ink).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize()) {
                UpdateBanner(vm, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                AnimatedContent(
                    targetState = vm.tab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tab",
                    modifier = Modifier.weight(1f),
                ) { t ->
                    when (t) {
                        0 -> CalcScreen(vm)
                        1 -> GraphScreen(vm)
                        2 -> ConvertScreen(vm)
                        3 -> ToolsScreen(vm)
                        else -> MoreScreen(vm)
                    }
                }
                TabBar(vm.tab) { vm.selectTab(it) }
            }
            val msg = vm.message
            if (msg != null) {
                LaunchedEffect(msg) {
                    delay(2200)
                    vm.message = null
                }
                Snackbar(
                    containerColor = Palette.PanelHigh,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp, start = 24.dp, end = 24.dp),
                ) { Text(msg) }
            }
        }
    }
}

/** Pływający pasek w kształcie pigułki, jak dolne przyciski w OpenPDF. */
@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(Palette.PanelHigh)
            .border(1.dp, Palette.Line, RoundedCornerShape(50))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tabs.forEachIndexed { i, tab ->
            val sel = i == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) Color.White else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tab.icon, null, tint = if (sel) Color.Black else Palette.TextDim, modifier = Modifier.size(20.dp))
                Text(
                    tab.title,
                    color = if (sel) Color.Black else Palette.TextDim,
                    fontSize = 10.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}
