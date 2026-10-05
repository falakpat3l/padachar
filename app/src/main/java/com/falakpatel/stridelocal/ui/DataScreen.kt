package com.falakpatel.stridelocal.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falakpatel.stridelocal.data.Backup
import com.falakpatel.stridelocal.data.HealthConnectImport
import com.falakpatel.stridelocal.strideApp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
private fun dayLabel(epochDay: Long) = LocalDate.ofEpochDay(epochDay).format(dateFmt)

/** Data & settings: storage info, accent colour, Health Connect import, deletion. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DataScreen(accent: Color, onBack: (() -> Unit)? = null) {
    val app = LocalContext.current.strideApp
    val repo = app.stepRepository
    val scope = rememberCoroutineScope()
    val days by remember { repo.observeAll() }.collectAsStateWithLifecycle(emptyList())
    var message by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    var chunk by remember { mutableIntStateOf(1) }
    var count by remember { mutableIntStateOf(1) }
    var hue by remember { mutableFloatStateOf(-1f) }

    fun runImport() {
        message = "Importing from Health Connect..."
        app.appScope.launch {
            message = runCatching { HealthConnectImport.importAll(app) }
                .fold({ "Done: $it days imported or updated." }, { "Import failed: ${it.message}" })
        }
    }
    var backupMsg by remember { mutableStateOf<String?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) app.appScope.launch {
            backupMsg = runCatching { Backup.export(app, uri) }.fold({ "Saved $it records." }, { "Backup failed: ${it.message}" })
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) app.appScope.launch {
            backupMsg = runCatching { Backup.restore(app, uri) }.fold({ "Restored: $it records added or updated." }, { "Restore failed: ${it.message}" })
        }
    }
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        if (HealthConnectImport.STEPS in it) runImport() else message = "Permission not given, nothing imported."
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Data & settings") },
            navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Section("Storage") {
                    val since = days.lastOrNull()?.let { " since ${dayLabel(it.epochDay)}" } ?: ""
                    Text("${days.size} days saved$since.")
                    Text(
                        "Everything is stored only on this phone, with no time limit. It stays until you delete it here. " +
                            "Uninstalling erases it, so save a backup file first.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Section("Backup") {
                    Text(
                        "Save steps, food and your profile to a file you choose, and load it back on this or a new phone. " +
                            "Restoring never deletes anything.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { saveLauncher.launch("stridelocal-backup-${LocalDate.now()}.csv") }) { Text("Save backup") }
                        OutlinedButton(onClick = { openLauncher.launch(arrayOf("text/*", "application/octet-stream")) }) { Text("Restore") }
                    }
                    backupMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item {
                Section("Accent colour") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccentPresets.forEach { c ->
                            Box(
                                Modifier.size(34.dp).background(c, CircleShape)
                                    .then(if (c == accent) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                                    .clickable { scope.launch { app.userPreferences.saveAccent(c.toArgb()) } },
                            )
                        }
                    }
                    Text("Custom hue", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = if (hue >= 0) hue else 0f,
                        onValueChange = { hue = it },
                        onValueChangeFinished = {
                            scope.launch { app.userPreferences.saveAccent(Color.hsv(hue, 0.8f, 0.95f).toArgb()) }
                        },
                        valueRange = 0f..359f,
                    )
                    if (hue >= 0) Box(Modifier.fillMaxWidth().size(8.dp).background(Color.hsv(hue, 0.8f, 0.95f), CircleShape))
                }
            }
            item {
                Section("Import old steps") {
                    Text(
                        "Copy past days from Google Fit / Google Health through Health Connect. It runs on the phone, " +
                            "no internet. In Google Fit, turn on Health Connect sync first.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = {
                        when (HealthConnectImport.status(app)) {
                            HealthConnectClient.SDK_AVAILABLE -> scope.launch {
                                if (HealthConnectImport.hasPermission(app)) runImport() else hcLauncher.launch(HealthConnectImport.PERMISSIONS)
                            }
                            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                                message = "Please install or update Health Connect from the Play Store."
                            else -> message = "Health Connect is not available on this phone."
                        }
                    }) { Text("Import from Health Connect") }
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item {
                Section("Delete oldest days") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 5).forEach { c ->
                            FilterChip(selected = chunk == c, onClick = { chunk = c; count = 1 }, label = { Text(if (c == 1) "1 day steps" else "5 day steps") })
                        }
                    }
                    val n = (count * chunk).coerceAtMost(days.size)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { if (count > 1) count-- }) { Text("-") }
                        Text("$n days", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { if (count * chunk < days.size) count++ }) { Text("+") }
                    }
                    Button(enabled = n > 0, onClick = {
                        val oldest = days.takeLast(n)
                        confirm = Pair(
                            "Delete $n days, ${dayLabel(oldest.last().epochDay)} to ${dayLabel(oldest.first().epochDay)}?",
                            suspend { repo.deleteOldest(n) },
                        )
                    }) { Text("Delete $n oldest days") }
                }
            }
            item { Text("All days (newest first)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
            items(days, key = { it.epochDay }) { d ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(dayLabel(d.epochDay))
                        Text(
                            String.format(Locale.getDefault(), "%,d steps  |  %.2f km  |  %.0f kcal", d.steps, d.distanceKm, d.activeKcal),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { confirm = Pair("Delete ${dayLabel(d.epochDay)}?", suspend { repo.deleteDay(d.epochDay) }) }) {
                        Icon(Icons.Filled.Delete, "Delete day", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    confirm?.let { (text, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Delete data?") },
            text = { Text("$text This cannot be undone.") },
            confirmButton = { TextButton(onClick = { app.appScope.launch { action() }; confirm = null; count = 1 }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
