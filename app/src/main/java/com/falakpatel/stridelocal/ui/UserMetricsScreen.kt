package com.falakpatel.stridelocal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.falakpatel.stridelocal.data.UserProfile
import com.falakpatel.stridelocal.health.HealthMetrics
import com.falakpatel.stridelocal.health.Sex
import java.util.Locale

/** Profile form with live BMI, BMR and stride preview. Values are validated before saving. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserMetricsScreen(initial: UserProfile, onSave: (UserProfile) -> Unit, onBack: (() -> Unit)?) {
    var weight by rememberSaveable { mutableStateOf(trim(initial.weightKg)) }
    var height by rememberSaveable { mutableStateOf(trim(initial.heightCm)) }
    var age by rememberSaveable { mutableStateOf(initial.ageYears.toString()) }
    var sex by rememberSaveable { mutableStateOf(initial.sex) }
    var goal by rememberSaveable { mutableStateOf(initial.dailyGoal.toString()) }
    var stride by rememberSaveable { mutableStateOf(initial.strideOverrideM?.let { trim(it) } ?: "") }
    var food by rememberSaveable { mutableStateOf(if (initial.foodGoalKcal > 0) initial.foodGoalKcal.toString() else "") }

    val w = weight.toDoubleOrNull()?.takeIf { it in 20.0..300.0 }
    val h = height.toDoubleOrNull()?.takeIf { it in 100.0..250.0 }
    val a = age.toIntOrNull()?.takeIf { it in 10..100 }
    val g = goal.toIntOrNull()?.takeIf { it in 500..100_000 }
    val s = stride.toDoubleOrNull()?.takeIf { it in 0.3..1.5 }
    val strideOk = stride.isBlank() || s != null
    val fg = food.toIntOrNull()?.takeIf { it in 500..6000 }
    val foodOk = food.isBlank() || fg != null
    val valid = w != null && h != null && a != null && g != null && strideOk && foodOk

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NumberField("Weight (kg)", weight, w == null) { weight = it }
            NumberField("Height (cm)", height, h == null) { height = it }
            NumberField("Age (years)", age, a == null, decimal = false) { age = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                Sex.entries.forEach { option ->
                    FilterChip(
                        selected = sex == option,
                        onClick = { sex = option },
                        label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            NumberField("Daily step goal", goal, g == null, decimal = false) { goal = it }
            NumberField("Stride in m (optional)", stride, !strideOk) { stride = it }
            Text(
                "Blank = auto from height.",
                style = MaterialTheme.typography.bodySmall,
            )
            NumberField("Daily food goal in kcal (optional)", food, !foodOk, decimal = false) { food = it }
            Text(
                "Used by the eaten ring. Blank = compare with the kcal you use each day.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (w != null && h != null && a != null) {
                val bmi = HealthMetrics.bmi(w, h)
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(String.format(Locale.getDefault(), "BMI %.1f (%s)", bmi, HealthMetrics.bmiCategory(bmi).label))
                        Text(String.format(Locale.getDefault(), "BMR %.0f kcal/day", HealthMetrics.bmrMifflinStJeor(w, h, a, sex)))
                        Text(String.format(Locale.getDefault(), "Stride %.2f m", s ?: HealthMetrics.strideMeters(h)))
                    }
                }
            }

            Button(
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onSave(initial.copy(weightKg = w!!, heightCm = h!!, ageYears = a!!, sex = sex, dailyGoal = g!!, strideOverrideM = s, foodGoalKcal = fg ?: 0, isConfigured = true))
                },
            ) { Text("Save") }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, isError: Boolean, decimal: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onChange(new.filter { it.isDigit() || (decimal && it == '.') }) },
        label = { Text(label) },
        isError = isError && value.isNotEmpty(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun trim(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
