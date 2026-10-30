package com.blacksingularities.opencalc.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencalc.engine.AngleMode
import com.blacksingularities.opencalc.engine.Fmt
import com.blacksingularities.opencalc.engine.NumMode
import com.blacksingularities.opencalc.update.UpdateManager
import com.blacksingularities.opencalc.update.UpdateState
import com.blacksingularities.opencalc.ui.theme.Palette

const val AUTHOR = "Adam Rędzikowski"
const val AUTHOR_URL = "https://redzikowski.com"
const val COFFEE_URL = "https://buycoffee.to/adamredzikowski"

@Composable
fun MoreScreen(vm: AppViewModel) {
    val s = vm.settings
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("Obliczenia") {
            Text("Kąty", color = Palette.TextDim, fontSize = 13.sp)
            ChipRow(AngleMode.entries, s.angle, { mapOf(AngleMode.DEG to "Stopnie", AngleMode.RAD to "Radiany", AngleMode.GRAD to "Grady").getValue(it) }, { vm.updateSettings(s.copy(angle = it)) })
            Text("Zapis wyników", color = Palette.TextDim, fontSize = 13.sp)
            ChipRow(NumMode.entries, s.style.mode, { it.label }, { vm.updateSettings(s.copy(style = s.style.copy(mode = it))) })
            Text("Cyfry znaczące: ${s.style.digits}", color = Palette.TextDim, fontSize = 13.sp)
            Slider(
                value = s.style.digits.toFloat(),
                onValueChange = { vm.updateSettings(s.copy(style = s.style.copy(digits = it.toInt()))) },
                valueRange = 4f..15f,
                steps = 10,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Palette.Line),
            )
            Hint("Przykład: " + Fmt.number(Math.PI * 1_000_000, s.style) + " · " + Fmt.number(1.0 / 7, s.style))
        }
        Section("Wygląd i obsługa") {
            SettingSwitch(Icons.Rounded.Numbers, "Przecinek dziesiętny", "1,5 zamiast 1.5; argumenty funkcji oddziela średnik", s.style.decimalComma) {
                vm.updateSettings(s.copy(style = s.style.copy(decimalComma = it)))
            }
            SettingSwitch(Icons.Rounded.SpaceBar, "Grupowanie cyfr", "1 234 567 zamiast 1234567", s.style.grouping) {
                vm.updateSettings(s.copy(style = s.style.copy(grouping = it)))
            }
            SettingSwitch(Icons.Rounded.Vibration, "Wibracje klawiszy", null, s.haptics) { vm.updateSettings(s.copy(haptics = it)) }
            SettingSwitch(Icons.Rounded.LightMode, "Ekran zawsze włączony", "Gdy aplikacja jest otwarta", s.keepScreenOn) { vm.updateSettings(s.copy(keepScreenOn = it)) }
            SettingSwitch(Icons.Rounded.DarkMode, "Panel funkcji naukowych", "Widoczny nad klawiaturą w pionie", s.sciOpen) { vm.updateSettings(s.copy(sciOpen = it)) }
        }
        Section("Aktualizacje") {
            SettingSwitch(Icons.Rounded.SystemUpdate, "Sprawdzaj automatycznie", "Co kilka godzin, przy otwarciu aplikacji", s.autoUpdate) { vm.updateSettings(s.copy(autoUpdate = it)) }
            val status = when (val u = vm.update) {
                UpdateState.Checking -> "Sprawdzanie…"
                UpdateState.UpToDate -> "Masz najnowszą wersję"
                is UpdateState.Available -> "Dostępna wersja ${u.info.version}"
                is UpdateState.Downloading -> "Pobieranie ${u.info.version}…"
                is UpdateState.Ready -> "Wersja ${u.info.version} pobrana"
                is UpdateState.Installing -> "Instalowanie…"
                is UpdateState.Failed -> u.message
                UpdateState.Idle -> "Wersja ${UpdateManager.currentVersion}"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(status, color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (vm.update is UpdateState.Available || vm.update is UpdateState.Ready) PillButton("Aktualizuj") { vm.updateNow() }
                else PillButton("Sprawdź", primary = false) { vm.checkUpdate(silent = false) }
            }
        }
        Section("O aplikacji") {
            Text("OpenCalc ${UpdateManager.currentVersion}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "Kalkulator naukowy z wykresami, przelicznikami jednostek i kilkunastoma narzędziami. Bez reklam, śledzenia i konta – wszystko liczy się w telefonie.",
                color = Palette.TextDim,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            ActionLine(Icons.Rounded.Language, "redzikowski.com", "Inne aplikacje autora") { uri.openUri(AUTHOR_URL) }
            ActionLine(Icons.Rounded.Coffee, "Postaw kawę", "Wesprzyj rozwój na buycoffee.to") { uri.openUri(COFFEE_URL) }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.Center) {
            Text("Pomysł i wykonanie: $AUTHOR · redzikowski.com", color = Palette.TextFaint, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingSwitch(icon: ImageVector, title: String, subtitle: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }, verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 14.sp, color = Color.White)
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = Palette.TextDim, lineHeight = 14.sp)
        }
        Switch(
            checked = value,
            onCheckedChange = onChange,
            modifier = Modifier.scale(0.82f),
            colors = SwitchDefaults.colors(checkedTrackColor = Color.White, checkedThumbColor = Color.Black),
        )
    }
}

@Composable
private fun ActionLine(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(icon)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 14.sp, color = Color.White)
            Text(subtitle, fontSize = 11.sp, color = Palette.TextDim)
        }
    }
}
