package com.falakpatel.stridelocal.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.falakpatel.stridelocal.data.DailySteps
import com.falakpatel.stridelocal.data.UserProfile
import com.falakpatel.stridelocal.sensor.StepCounterService
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(state: MainUiState, onEditProfile: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("StrideLocal") },
                actions = {
                    IconButton(onClick = onEditProfile) { Icon(Icons.Filled.Person, contentDescription = "Your metrics") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PermissionGate()
            TodayCard(state.today, state.profile.dailyGoal)
            WeekCard(state.week, state.profile.dailyGoal)
            BodyCard(state.profile)
            Text(
                "All data stays on this phone. No internet permission, no ads, no accounts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Asks for ACTIVITY_RECOGNITION (Android 10+) and POST_NOTIFICATIONS (Android 13+),
 * then starts the service. If the user says no, explains why and links to settings.
 */
@Composable
private fun PermissionGate() {
    val context = LocalContext.current
    val needed = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    var activityGranted by remember { mutableStateOf(StepCounterService.hasActivityPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        activityGranted = StepCounterService.hasActivityPermission(context)
        if (activityGranted) StepCounterService.start(context)
    }
    LaunchedEffect(Unit) {
        if (activityGranted) StepCounterService.start(context) else if (needed.isNotEmpty()) launcher.launch(needed)
    }
    if (activityGranted) return

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Physical activity permission needed", fontWeight = FontWeight.Bold)
            Text("Android only shares step data with apps that have this permission. It never leaves your phone.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launcher.launch(needed) }) { Text("Allow") }
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text("Open settings") }
            }
        }
    }
}

@Composable
private fun TodayCard(today: DailySteps, goal: Int) {
    val progress = if (goal > 0) (today.steps.toFloat() / goal).coerceIn(0f, 1f) else 0f
    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(trackColor, -90f, 360f, false, style = stroke)
                    drawArc(ringColor, -90f, 360f * progress, false, style = stroke)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(String.format(Locale.getDefault(), "%,d", today.steps), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text("of ${String.format(Locale.getDefault(), "%,d", goal)} steps", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(String.format(Locale.getDefault(), "%.2f", today.distanceKm), "km")
                Stat(String.format(Locale.getDefault(), "%.0f", today.activeKcal), "active kcal")
                Stat("${(progress * 100).toInt()}%", "of goal")
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WeekCard(week: List<DailySteps>, goal: Int) {
    if (week.isEmpty()) return
    val bar = MaterialTheme.colorScheme.primary
    val goalLine = MaterialTheme.colorScheme.tertiary
    val maxSteps = maxOf(goal.toLong(), week.maxOf { it.steps }, 1L)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val slot = size.width / week.size
                val barWidth = slot * 0.55f
                week.forEachIndexed { i, d ->
                    val h = size.height * d.steps / maxSteps
                    drawRoundRect(
                        color = bar,
                        topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                }
                val y = size.height - size.height * goal / maxSteps
                drawLine(goalLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            }
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    Text(
                        LocalDate.ofEpochDay(d.epochDay).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun BodyCard(p: UserProfile) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat(String.format(Locale.getDefault(), "%.1f", p.bmi), "BMI, ${p.bmiCategory.label}")
            Stat(String.format(Locale.getDefault(), "%.0f", p.bmr), "BMR kcal/day")
            Stat(String.format(Locale.getDefault(), "%.2f m", p.strideM), "stride")
        }
    }
}
