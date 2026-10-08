package com.falakpatel.stridelocal.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.falakpatel.stridelocal.data.UserProfile
import com.falakpatel.stridelocal.strideApp
import com.falakpatel.stridelocal.ui.StrideTheme
import kotlinx.coroutines.launch

/**
 * "Widget settings" screen: pick what one widget shows (steps, distance or calorie deficit).
 * Android opens it when the widget is placed (older phones) or from long-press, "Widget settings".
 */
class WidgetSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Say OK straight away, so backing out still keeps the widget (showing steps).
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(widgetId)

        setContent {
            val profile by strideApp.userPreferences.profile.collectAsState(null)
            StrideTheme(Color(profile?.accentArgb ?: UserProfile.DEFAULT_ACCENT)) {
                var chosen by remember { mutableStateOf(WidgetMetric.STEPS) }
                val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) { // start on this widget's current choice
                    val state = getAppWidgetState(this@WidgetSettingsActivity, PreferencesGlanceStateDefinition, glanceId)
                    chosen = WidgetMetric.from(state[StepWidget.METRIC])
                }
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Widget shows", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))
                        WidgetMetric.entries.forEach { m ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .selectable(selected = chosen == m, onClick = { chosen = m }, role = Role.RadioButton)
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = chosen == m, onClick = null)
                                Spacer(Modifier.width(12.dp))
                                Text(m.title, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            scope.launch {
                                updateAppWidgetState(this@WidgetSettingsActivity, glanceId) { it[StepWidget.METRIC] = chosen.name }
                                StepWidget().update(this@WidgetSettingsActivity, glanceId)
                                finish()
                            }
                        }) { Text("Done") }
                    }
                }
            }
        }
    }
}
