package com.blacksingularities.opencalc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

/**
 * Pasek zakładek bez tła i obramowania, przyklejony do dolnej krawędzi. Wybraną
 * zakładkę znaczy biały tekst z ikoną i kropka pod podpisem – bez pigułki, żeby
 * nic nie odcinało się od czarnego tła.
 */
@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        tabs.forEachIndexed { i, tab ->
            val sel = i == selected
            val tint by animateColorAsState(if (sel) Color.White else Palette.TextDim, label = "tabTint")
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 36.dp),
                    ) { onSelect(i) }
                    .padding(top = 8.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tab.icon, null, tint = tint, modifier = Modifier.size(21.dp))
                Spacer(Modifier.height(3.dp))
                Text(
                    tab.title,
                    color = tint,
                    fontSize = 10.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (sel) Color.White else Color.Transparent),
                )
            }
        }
    }
}
