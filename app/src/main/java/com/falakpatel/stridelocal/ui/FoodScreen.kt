package com.falakpatel.stridelocal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falakpatel.stridelocal.data.Dish
import com.falakpatel.stridelocal.data.FoodEntry
import com.falakpatel.stridelocal.data.IndianDishes
import com.falakpatel.stridelocal.sensor.DayClock
import com.falakpatel.stridelocal.strideApp
import kotlinx.coroutines.launch
import java.util.Locale

private fun fmt(v: Double, digits: Int = 0) = String.format(Locale.getDefault(), "%.${digits}f", v)

private fun newEntry(name: String, servings: Double, kcal: Double, p: Double, c: Double, f: Double): FoodEntry {
    val now = System.currentTimeMillis()
    return FoodEntry(epochDay = DayClock.epochDay(now), timeMs = now, name = name, servings = servings,
        kcal = kcal * servings, proteinG = p * servings, carbsG = c * servings, fatG = f * servings)
}

/**
 * Food log, step 1: pick from a built-in Indian dish list (or add your own) and see today's
 * balance: eaten vs burned (BMR for the whole day + active kcal from walking).
 * Photo recognition will plug into this same log in a later version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodScreen(state: MainUiState) {
    val app = LocalContext.current.strideApp
    val repo = app.stepRepository
    val entries by remember { repo.observeFoodToday() }.collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var picked by remember { mutableStateOf<Dish?>(null) }
    var custom by remember { mutableStateOf(false) }
    val dishes = remember(query) { IndianDishes.search(query) }

    val eaten = entries.sumOf { it.kcal }
    val burned = state.profile.bmr + state.today.activeKcal
    val balance = eaten - burned

    Scaffold(topBar = { TopAppBar(title = { Text("Food") }) }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Stat2(fmt(eaten), "eaten")
                            Stat2(fmt(burned), "burned")
                            Stat2((if (balance > 0) "+" else "") + fmt(balance), if (balance <= 0) "deficit" else "surplus")
                        }
                        Text(
                            "Protein ${fmt(entries.sumOf { it.proteinG })} g  |  Carbs ${fmt(entries.sumOf { it.carbsG })} g  |  Fat ${fmt(entries.sumOf { it.fatG })} g",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Burned = BMR for the whole day + active kcal from steps.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (entries.isNotEmpty()) {
                item { Text("Today", style = MaterialTheme.typography.titleSmall) }
                items(entries, key = { it.id }) { e ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name + if (e.servings != 1.0) " x ${fmt(e.servings, 1)}" else "")
                            Text("${fmt(e.kcal)} kcal  |  P ${fmt(e.proteinG)} g", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { app.appScope.launch { repo.deleteFood(e.id) } }) {
                            Icon(Icons.Filled.Delete, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Column {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        label = { Text("Search dishes (dal, roti, dhokla...)") }, modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { custom = true }) { Text("+ Add your own food") }
                }
            }
            items(dishes, key = { it.name }) { d ->
                Column(Modifier.fillMaxWidth().clickable { picked = d }.padding(vertical = 6.dp)) {
                    Text(d.name)
                    Text("${d.serving}  |  ${fmt(d.kcal)} kcal  |  P ${fmt(d.protein)} g", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Text(
                    "Values are typical home-style estimates per serving. Oil, ghee and portion size change them.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }

    picked?.let { d ->
        var servings by remember(d) { mutableDoubleStateOf(1.0) }
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text(d.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1 serving = ${d.serving}")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { if (servings > 0.5) servings -= 0.5 }) { Text("-") }
                        Text("${fmt(servings, 1)} serving", fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { if (servings < 10) servings += 0.5 }) { Text("+") }
                    }
                    Text("${fmt(d.kcal * servings)} kcal")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val e = newEntry(d.name, servings, d.kcal, d.protein, d.carbs, d.fat)
                    app.appScope.launch { repo.addFood(e) }
                    picked = null
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { picked = null }) { Text("Cancel") } },
        )
    }

    if (custom) {
        var name by remember { mutableStateOf("") }
        var kcal by remember { mutableStateOf("") }
        var protein by remember { mutableStateOf("") }
        val k = kcal.toDoubleOrNull()
        AlertDialog(
            onDismissRequest = { custom = false },
            title = { Text("Add your own food") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(kcal, { kcal = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("kcal") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(protein, { protein = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Protein g (optional)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && k != null, onClick = {
                    val e = newEntry(name.trim(), 1.0, k ?: 0.0, protein.toDoubleOrNull() ?: 0.0, 0.0, 0.0)
                    app.appScope.launch { repo.addFood(e) }
                    custom = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { custom = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Stat2(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
