package com.falakpatel.stridelocal.ui

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.falakpatel.stridelocal.R
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import com.falakpatel.stridelocal.data.Backup
import com.falakpatel.stridelocal.reminder.MoveReminder
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

    fun runImport() {
        message = "Importing..."
        app.appScope.launch {
            message = runCatching { HealthConnectImport.importAll(app) }
                .fold({ "$it days imported." }, { "Import failed: ${it.message}" })
        }
    }
    var backupMsg by remember { mutableStateOf<String?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) app.appScope.launch {
            backupMsg = runCatching { Backup.export(app, uri) }.fold({ "Saved." }, { "Backup failed: ${it.message}" })
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) app.appScope.launch {
            backupMsg = runCatching { Backup.restore(app, uri) }.fold({ "Restored $it records." }, { "Restore failed: ${it.message}" })
        }
    }
    val reminders by remember { app.userPreferences.moveReminders }.collectAsStateWithLifecycle(false)
    fun setReminders(on: Boolean) {
        scope.launch { app.userPreferences.setMoveReminders(on) }
        if (on) MoveReminder.scheduleNext(app) else MoveReminder.cancel(app)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) setReminders(true)
    }
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        if (HealthConnectImport.STEPS in it) runImport() else message = "Not allowed."
    }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("Settings") },
            navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Section("Data") {
                    val since = days.lastOrNull()?.let { " since ${dayLabel(it.epochDay)}" } ?: ""
                    Text("${days.size} days$since.")
                    Text(
                        "On this phone only. Uninstalling deletes it, so keep a backup.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Section("Reminder") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "A quiet nudge about every 1.5 h, 7 am to 9 pm.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = reminders, onCheckedChange = { on ->
                            if (on && !MoveReminder.hasPermission(app)) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else setReminders(on)
                        })
                    }
                }
            }
            item {
                Section("Backup") {
                    Text(
                        "Steps, food and profile in one file.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { saveLauncher.launch("padachar-backup-${LocalDate.now()}.csv") }) { Text("Save backup") }
                        OutlinedButton(onClick = { openLauncher.launch(arrayOf("text/*", "application/octet-stream")) }) { Text("Restore") }
                    }
                    backupMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item {
                Section("Colour") {
                    // One short row of swatches. Tap one to use it across the app.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccentPresets.forEach { c ->
                            Box(
                                Modifier.size(28.dp).background(c, CircleShape)
                                    .then(if (c == accent) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                                    .clickable { scope.launch { app.userPreferences.saveAccent(c.toArgb()) } },
                            )
                        }
                    }
                }
            }
            item {
                Section("Google Fit history") {
                    Text(
                        "Via Health Connect. Turn on Health Connect sync in Google Fit first.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = {
                        when (HealthConnectImport.status(app)) {
                            HealthConnectClient.SDK_AVAILABLE -> scope.launch {
                                if (HealthConnectImport.hasPermission(app)) runImport() else hcLauncher.launch(HealthConnectImport.PERMISSIONS)
                            }
                            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                                message = "Update Health Connect from the Play Store."
                            else -> message = "Health Connect not available."
                        }
                    }) { Text("Import") }
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item {
                Section("Delete") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 5).forEach { c ->
                            FilterChip(selected = chunk == c, onClick = { chunk = c; count = 1 }, label = { Text(if (c == 1) "1 day" else "5 days") })
                        }
                    }
                    val n = (count * chunk).coerceAtMost(days.size)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { if (count > 1) count-- }) { Text("-") }
                        Text(if (n == 1) "1 day" else "$n days", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { if (count * chunk < days.size) count++ }) { Text("+") }
                    }
                    Button(enabled = n > 0, onClick = {
                        val oldest = days.takeLast(n)
                        confirm = Pair(
                            "Delete $n days, ${dayLabel(oldest.last().epochDay)} to ${dayLabel(oldest.first().epochDay)}?",
                            suspend { repo.deleteOldest(n) },
                        )
                    }) { Text("Delete oldest") }
                }
            }
            item { AboutSection() }
            item { Text("All days", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
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
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

// About the app and its maker. Links open in the phone's browser; the app
// itself still has no internet permission.
@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    fun open(url: String) = runCatching { uriHandler.openUri(url) }

    Section("About") {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = "padachar logo", modifier = Modifier.size(72.dp))
        Text("padachar $version", style = MaterialTheme.typography.titleSmall)
        Text("Falak", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        // Tap a symbol to open the link in the browser. Symbols: res/drawable/ic_web.xml etc.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                Triple(R.drawable.ic_web, "Website", "https://falakpatel.com"),
                Triple(R.drawable.ic_github, "Source code", "https://github.com/falakpat3l/stride-local"),
                Triple(R.drawable.ic_scholar, "Google Scholar", "https://scholar.google.com/citations?user=s6-Bs10AAAAJ"),
                Triple(R.drawable.ic_linkedin, "LinkedIn", "https://www.linkedin.com/in/falak-pat3l"),
                Triple(R.drawable.ic_x, "X", "https://x.com/falakpat3l"),
            ).forEach { (icon, name, url) ->
                IconButton(onClick = { open(url) }) {
                    Icon(painterResource(icon), contentDescription = name, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
