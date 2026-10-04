package com.falakpatel.stridelocal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.falakpatel.stridelocal.StrideApp
import com.falakpatel.stridelocal.data.DailySteps
import com.falakpatel.stridelocal.data.StepRepository
import com.falakpatel.stridelocal.data.UserPreferences
import com.falakpatel.stridelocal.data.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val loaded: Boolean = false,
    val profile: UserProfile = UserProfile(),
    val today: DailySteps = DailySteps(0),
    val week: List<DailySteps> = emptyList(),
)

class MainViewModel(
    private val prefs: UserPreferences,
    repository: StepRepository,
) : ViewModel() {

    val uiState: StateFlow<MainUiState> =
        combine(prefs.profile, repository.observeToday(), repository.observeHistory(7)) { p, t, w ->
            MainUiState(loaded = true, profile = p, today = t, week = w)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch { prefs.save(profile) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StrideApp
                MainViewModel(app.userPreferences, app.stepRepository)
            }
        }
    }
}
