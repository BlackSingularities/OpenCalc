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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ripple
import androidx.compose.material3.Snackbar
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
    CompositionLocalProvider(LocalVm provides vm, LocalCorner provides vm.settings.corner.dp) {
        BackHandler(enabled = vm.tab != 0) { vm.selectTab(0) }
        BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Ink).windowInsetsPadding(WindowInsets.safeDrawing)) {
            // Szerokie okno (tablet poziomo, rozłożony składak) – zakładki w pionie z boku,
            // żeby nie zabierały wysokości klawiaturze i wykresom.
            val rail = maxWidth >= 840.dp && maxWidth > maxHeight
            Row(Modifier.fillMaxSize()) {
                if (rail) TabRail(vm.tab) { vm.selectTab(it) }
                Column(Modifier.weight(1f).fillMaxHeight()) {
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
                    if (!rail) TabBar(vm.tab) { vm.selectTab(it) }
                }
            }
            val msg = vm.message
            if (msg != null) {
                LaunchedEffect(msg) {
                    delay(2200)
                    vm.message = null
                }
                Snackbar(
                    containerColor = Palette.PanelHigh,
                    contentColor = Palette.Fg,
                    shape = buttonShape(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp, start = 24.dp, end = 24.dp),
                ) { Text(msg) }
            }
        }
    }
}

/**
 * Pasek zakładek przyklejony do dolnej krawędzi. Wybrana zakładka to biały,
 * prawie kwadratowy kafel (promień z ustawień) z czarną ikoną i podpisem.
 */
@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val shape = buttonShape()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { i, tab ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) Palette.Fg else Color.Transparent, label = "tabBg")
            val tint by animateColorAsState(if (sel) Palette.Bg else Palette.TextDim, label = "tabTint")
            Column(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(bg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                    ) { onSelect(i) }
                    .semantics { role = Role.Tab; this.selected = sel }
                    .padding(top = 7.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tab.icon, null, tint = tint, modifier = Modifier.size(21.dp))
                Spacer(Modifier.height(3.dp))
                Text(
                    tab.title,
                    color = tint,
                    fontSize = 10.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** Zakładki w pionowym pasku po lewej – na szerokich ekranach zamiast dolnego paska. */
@Composable
private fun TabRail(selected: Int, onSelect: (Int) -> Unit) {
    val shape = buttonShape()
    Column(
        Modifier.width(92.dp).fillMaxHeight().padding(horizontal = 6.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        tabs.forEachIndexed { i, tab ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) Palette.Fg else Color.Transparent, label = "railBg")
            val tint by animateColorAsState(if (sel) Palette.Bg else Palette.TextDim, label = "railTint")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(bg)
                    .clickable { onSelect(i) }
                    .semantics { role = Role.Tab; this.selected = sel }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tab.icon, null, tint = tint, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(4.dp))
                Text(tab.title, color = tint, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false)
            }
        }
    }
}
