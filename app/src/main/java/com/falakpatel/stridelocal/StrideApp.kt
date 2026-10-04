package com.falakpatel.stridelocal

import android.app.Application
import android.content.Context
import androidx.glance.appwidget.updateAll
import com.falakpatel.stridelocal.data.StepDatabase
import com.falakpatel.stridelocal.data.StepRepository
import com.falakpatel.stridelocal.data.UserPreferences
import com.falakpatel.stridelocal.data.userDataStore
import com.falakpatel.stridelocal.widget.StepWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Tiny manual DI container. No frameworks, nothing that phones home. */
class StrideApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database by lazy { StepDatabase.build(this) }
    val userPreferences by lazy { UserPreferences(userDataStore) }
    val stepRepository by lazy {
        StepRepository(database.stepDao(), appScope) { snap ->
            if (snap.refreshWidget) StepWidget().updateAll(this)
        }
    }
}

val Context.strideApp: StrideApp get() = applicationContext as StrideApp
