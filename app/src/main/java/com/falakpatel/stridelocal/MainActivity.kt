package com.falakpatel.stridelocal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falakpatel.stridelocal.ui.MainScreen
import com.falakpatel.stridelocal.ui.MainViewModel
import com.falakpatel.stridelocal.ui.StrideTheme
import com.falakpatel.stridelocal.ui.UserMetricsScreen

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StrideTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                var editing by rememberSaveable { mutableStateOf(false) }
                if (!state.loaded) return@StrideTheme

                // First launch goes straight to the profile form.
                val showMetrics = editing || !state.profile.isConfigured
                BackHandler(enabled = editing && state.profile.isConfigured) { editing = false }

                if (showMetrics) {
                    UserMetricsScreen(
                        initial = state.profile,
                        onSave = { viewModel.saveProfile(it); editing = false },
                        onBack = if (state.profile.isConfigured) ({ editing = false }) else null,
                    )
                } else {
                    MainScreen(state = state, onEditProfile = { editing = true })
                }
            }
        }
    }
}
