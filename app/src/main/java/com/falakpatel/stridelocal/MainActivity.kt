package com.falakpatel.stridelocal

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falakpatel.stridelocal.data.UserProfile
import com.falakpatel.stridelocal.sensor.StepSync
import com.falakpatel.stridelocal.ui.DataScreen
import com.falakpatel.stridelocal.ui.MainScreen
import com.falakpatel.stridelocal.ui.MainViewModel
import com.falakpatel.stridelocal.ui.StrideTheme
import com.falakpatel.stridelocal.ui.UserMetricsScreen
import kotlinx.coroutines.launch

private enum class Screen { HOME, METRICS, DATA }

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = dark, navigationBarStyle = dark)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            StrideTheme(Color(state.profile.accentArgb)) {
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                if (!state.loaded) return@StrideTheme
                // First launch goes straight to the profile form.
                val current = if (!state.profile.isConfigured) Screen.METRICS else screen
                BackHandler(enabled = current != Screen.HOME) { screen = Screen.HOME }
                when (current) {
                    Screen.HOME -> MainScreen(state, onEditProfile = { screen = Screen.METRICS }, onOpenData = { screen = Screen.DATA })
                    Screen.METRICS -> UserMetricsScreen(
                        initial = state.profile,
                        onSave = { viewModel.saveProfile(it); screen = Screen.HOME },
                        onBack = if (state.profile.isConfigured) ({ screen = Screen.HOME }) else null,
                    )
                    Screen.DATA -> DataScreen(Color(state.profile.accentArgb), onBack = { screen = Screen.HOME })
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Pull the freshest count the moment the app is opened.
        strideApp.appScope.launch { StepSync.syncNow(applicationContext) }
    }
}

/** Shown by Health Connect when the user asks why the app wants step data. */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StrideTheme(Color(UserProfile.DEFAULT_ACCENT)) {
                Surface(Modifier.fillMaxSize()) {
                    Text(
                        "StrideLocal privacy policy\n\n" +
                            "StrideLocal has no internet permission. It reads step and distance history from " +
                            "Health Connect only when you tap Import, and stores it in the app's private storage " +
                            "on this phone. Nothing is uploaded, shared or sold. Deleting data in the app, or " +
                            "uninstalling it, removes it.",
                        modifier = Modifier.safeDrawingPadding().padding(24.dp),
                    )
                }
            }
        }
    }
}
