package com.falakpatel.stridelocal.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.falakpatel.stridelocal.labs.CameraPpgMonitor
import com.falakpatel.stridelocal.labs.PostureMonitor
import com.falakpatel.stridelocal.labs.RespirationMonitor
import kotlinx.coroutines.delay
import java.util.Locale

private enum class Probe { HEART, BREATH, POSTURE }

/**
 * Measure tab: heart rate (camera + flash), breathing rate (accelerometer on the chest) and
 * posture alerts (proximity + tilt). Only one runs at a time and everything stops when you
 * leave the tab. Nothing is recorded or saved; camera frames never leave memory.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasureScreen() {
    val ctx = LocalContext.current.applicationContext
    val heart = remember { CameraPpgMonitor(ctx) }
    val breath = remember { RespirationMonitor(ctx) }
    val posture = remember { PostureMonitor(ctx) }
    var active by remember { mutableStateOf<Probe?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var seconds by remember { mutableIntStateOf(0) }

    fun stopAll() {
        heart.stop(); breath.stop(); posture.stop()
        active = null
    }
    fun startHeart() {
        stopAll()
        if (heart.start()) active = Probe.HEART else note = "No rear camera with flash found."
    }
    DisposableEffect(Unit) { onDispose { heart.stop(); breath.stop(); posture.stop() } }
    LaunchedEffect(active) {
        seconds = 0
        while (active != null) { delay(1_000); seconds++ }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startHeart() else note = "Camera permission is needed for heart rate. Frames are never saved."
    }

    val bpm by heart.bpm.collectAsState()
    val br by breath.breathsPerMinute.collectAsState()
    val ps by posture.state.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Measure") }) }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProbeCard(
                title = "Heart rate",
                howTo = "Cover the back camera and flash with one fingertip. Press lightly and keep still for about 15 seconds.",
                value = when {
                    active != Probe.HEART -> bpm?.let { "Last: $it bpm" } ?: "--"
                    bpm != null -> "$bpm bpm"
                    else -> "Measuring... ${seconds}s"
                },
                running = active == Probe.HEART,
                onToggle = {
                    if (active == Probe.HEART) stopAll()
                    else if (heart.hasPermission()) startHeart()
                    else cameraLauncher.launch(Manifest.permission.CAMERA)
                },
            )
            ProbeCard(
                title = "Breathing rate",
                howTo = "Lie on your back, place the phone flat on your chest and breathe normally for about 30 seconds.",
                value = when {
                    active != Probe.BREATH -> br?.let { "Last: $it breaths/min" } ?: "--"
                    br != null -> "$br breaths/min"
                    else -> "Measuring... ${seconds}s"
                },
                running = active == Probe.BREATH,
                onToggle = {
                    if (active == Probe.BREATH) stopAll()
                    else { stopAll(); if (breath.start()) active = Probe.BREATH else note = "No accelerometer found." }
                },
            )
            ProbeCard(
                title = "Posture",
                howTo = "While this is on, it warns if the phone is too close to your face, or held low with your head bent for over a minute.",
                value = when {
                    active != Probe.POSTURE -> "Off"
                    ps.faceTooClose -> "Too close to your face"
                    ps.neckWarning -> "Raise your phone, your head is bent"
                    else -> String.format(Locale.getDefault(), "OK (phone tilt %.0f degrees)", ps.pitchDeg)
                },
                running = active == Probe.POSTURE,
                onToggle = {
                    if (active == Probe.POSTURE) stopAll() else { stopAll(); posture.start(); active = Probe.POSTURE }
                },
            )
            note?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(
                "Wellness estimates only, not a medical device.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProbeCard(title: String, howTo: String, value: String, running: Boolean, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(howTo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            if (running) OutlinedButton(onClick = onToggle) { Text("Stop") } else Button(onClick = onToggle) { Text("Start") }
        }
    }
}
