package com.falakpatel.stridelocal

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falakpatel.stridelocal.data.UserProfile
import com.falakpatel.stridelocal.sensor.StepSync
import com.falakpatel.stridelocal.ui.DataScreen
import com.falakpatel.stridelocal.ui.FoodScreen
import com.falakpatel.stridelocal.ui.MainScreen
import com.falakpatel.stridelocal.ui.MainViewModel
import com.falakpatel.stridelocal.ui.ActivityScreen
import com.falakpatel.stridelocal.ui.StrideTheme
import com.falakpatel.stridelocal.ui.UserMetricsScreen
import kotlinx.coroutines.launch

/** Bottom tabs, Google Fit style. A null icon means the app's own walking figure. */
private enum class Tab(val label: String, val icon: ImageVector?) {
    HOME("Home", Icons.Filled.Home),
    ACTIVITY("Activity", null),
    FOOD("Food", Icons.AutoMirrored.Filled.List),
    SETTINGS("Settings", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = dark, navigationBarStyle = dark)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val accent = Color(state.profile.accentArgb)
            StrideTheme(accent) {
                var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
                var editing by rememberSaveable { mutableStateOf(false) }
                if (!state.loaded) return@StrideTheme

                // First launch (or the profile button) shows the metrics form full screen.
                if (editing || !state.profile.isConfigured) {
                    BackHandler(enabled = editing && state.profile.isConfigured) { editing = false }
                    UserMetricsScreen(
                        initial = state.profile,
                        onSave = { viewModel.saveProfile(it); editing = false },
                        onBack = if (state.profile.isConfigured) ({ editing = false }) else null,
                    )
                    return@StrideTheme
                }

                BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }
                Scaffold(
                    bottomBar = {
                        NavigationBar(containerColor = Color.Black) {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t,
                                    onClick = { tab = t },
                                    icon = {
                                        if (t.icon != null) Icon(t.icon, contentDescription = t.label)
                                        else Icon(painterResource(R.drawable.ic_stat_steps), contentDescription = t.label)
                                    },
                                    label = { Text(t.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                )
                            }
                        }
                    },
                ) { pad ->
                    Box(Modifier.padding(bottom = pad.calculateBottomPadding())) {
                        when (tab) {
                            Tab.HOME -> MainScreen(state, onEditProfile = { editing = true })
                            Tab.FOOD -> FoodScreen(state)
                            Tab.ACTIVITY -> ActivityScreen(state)
                            Tab.SETTINGS -> DataScreen(accent)
                        }
                    }
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
