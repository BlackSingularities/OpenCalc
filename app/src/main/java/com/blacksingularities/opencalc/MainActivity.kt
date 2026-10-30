package com.blacksingularities.opencalc

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.blacksingularities.opencalc.ui.AppRoot
import com.blacksingularities.opencalc.ui.AppViewModel
import com.blacksingularities.opencalc.ui.theme.OpenCalcTheme
import com.blacksingularities.opencalc.update.UpdateManager

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        UpdateManager.clearUpdatedNotification(this)
        setContent {
            OpenCalcTheme {
                val keepOn = vm.settings.keepScreenOn
                LaunchedEffect(keepOn) {
                    if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                AppRoot(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.resumed()
    }
}
