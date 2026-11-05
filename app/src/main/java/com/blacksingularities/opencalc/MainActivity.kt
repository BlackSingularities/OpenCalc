package com.blacksingularities.opencalc

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import com.blacksingularities.opencalc.i18n.I18n
import com.blacksingularities.opencalc.ui.theme.ThemeMode
import com.blacksingularities.opencalc.ui.AppRoot
import com.blacksingularities.opencalc.ui.AppViewModel
import com.blacksingularities.opencalc.ui.theme.OpenCalcTheme
import com.blacksingularities.opencalc.update.UpdateManager

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyBars(dark = true)
        window.isNavigationBarContrastEnforced = false
        UpdateManager.clearUpdatedNotification(this)
        setContent {
            val dark = when (vm.settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            LaunchedEffect(dark) { applyBars(dark) }
            I18n.setting = vm.settings.language
            OpenCalcTheme(dark) {
                val keepOn = vm.settings.keepScreenOn
                LaunchedEffect(keepOn) {
                    if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                AppRoot(vm)
            }
        }
    }

    /** Paski systemowe przezroczyste, z ikonami w kolorze pasującym do motywu. */
    private fun applyBars(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        window.isNavigationBarContrastEnforced = false
    }

    override fun onResume() {
        super.onResume()
        vm.resumed()
    }
}
