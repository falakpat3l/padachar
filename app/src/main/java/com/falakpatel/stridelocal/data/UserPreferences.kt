package com.falakpatel.stridelocal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.falakpatel.stridelocal.health.HealthMetrics
import com.falakpatel.stridelocal.health.Sex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

data class UserProfile(
    val weightKg: Double = 70.0,
    val heightCm: Double = 170.0,
    val ageYears: Int = 25,
    val sex: Sex = Sex.MALE,
    val dailyGoal: Int = 8_000,
    /** Optional calibrated stride (walk 100 steps, measure distance). Null = height x 0.414. */
    val strideOverrideM: Double? = null,
    val isConfigured: Boolean = false,
    /** Accent colour (ARGB) chosen in Data & settings. */
    val accentArgb: Int = DEFAULT_ACCENT,
    /** Daily food goal in kcal for the eaten ring. 0 = none: compare with kcal used (BMR + active). */
    val foodGoalKcal: Int = 0,
) {
    val strideM: Double get() = strideOverrideM ?: HealthMetrics.strideMeters(heightCm)
    val bmi: Double get() = HealthMetrics.bmi(weightKg, heightCm)
    val bmiCategory get() = HealthMetrics.bmiCategory(bmi)
    val bmr: Double get() = HealthMetrics.bmrMifflinStJeor(weightKg, heightCm, ageYears, sex)

    // Default accent: the link blue from falakpatel.com
    companion object { const val DEFAULT_ACCENT = 0xFF8AB4F8.toInt() }
}

val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/** Weight, height, goal etc. in Jetpack DataStore (Preferences). File stays in app-private storage. */
class UserPreferences(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val WEIGHT = doublePreferencesKey("weight_kg")
        val HEIGHT = doublePreferencesKey("height_cm")
        val AGE = intPreferencesKey("age_years")
        val SEX = stringPreferencesKey("sex")
        val GOAL = intPreferencesKey("daily_goal")
        val STRIDE = doublePreferencesKey("stride_override_m")
        val CONFIGURED = booleanPreferencesKey("configured")
        val ACCENT = intPreferencesKey("accent_argb")
        val FOOD_GOAL = intPreferencesKey("food_goal_kcal")
        val MOVE_REMINDERS = booleanPreferencesKey("move_reminders")
        val GOAL_NOTICE = booleanPreferencesKey("goal_notice")
        val GOAL_CELEBRATED_DAY = longPreferencesKey("goal_celebrated_day")
        val GOAL_NOTIFIED_DAY = longPreferencesKey("goal_notified_day")
    }

    val profile: Flow<UserProfile> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            val d = UserProfile()
            UserProfile(
                weightKg = p[Keys.WEIGHT] ?: d.weightKg,
                heightCm = p[Keys.HEIGHT] ?: d.heightCm,
                ageYears = p[Keys.AGE] ?: d.ageYears,
                sex = p[Keys.SEX]?.let { runCatching { Sex.valueOf(it) }.getOrNull() } ?: d.sex,
                dailyGoal = p[Keys.GOAL] ?: d.dailyGoal,
                strideOverrideM = p[Keys.STRIDE],
                isConfigured = p[Keys.CONFIGURED] ?: false,
                accentArgb = p[Keys.ACCENT] ?: UserProfile.DEFAULT_ACCENT,
                foodGoalKcal = p[Keys.FOOD_GOAL] ?: 0,
            )
        }

    suspend fun save(profile: UserProfile) {
        dataStore.edit { p ->
            p[Keys.WEIGHT] = profile.weightKg
            p[Keys.HEIGHT] = profile.heightCm
            p[Keys.AGE] = profile.ageYears
            p[Keys.SEX] = profile.sex.name
            p[Keys.GOAL] = profile.dailyGoal
            if (profile.strideOverrideM != null) p[Keys.STRIDE] = profile.strideOverrideM else p.remove(Keys.STRIDE)
            p[Keys.FOOD_GOAL] = profile.foodGoalKcal
            p[Keys.CONFIGURED] = true
        }
    }

    val moveReminders: Flow<Boolean> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[Keys.MOVE_REMINDERS] ?: false }

    suspend fun setMoveReminders(on: Boolean) {
        dataStore.edit { it[Keys.MOVE_REMINDERS] = on }
    }

    /** "Daily goal reached" notification. On by default; Android 13+ still needs the permission. */
    val goalNotice: Flow<Boolean> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[Keys.GOAL_NOTICE] ?: true }

    suspend fun setGoalNotice(on: Boolean) {
        dataStore.edit { it[Keys.GOAL_NOTICE] = on }
    }

    /** Epoch day the Home screen last played its small "goal reached" animation (-1 = never). */
    val goalCelebratedDay: Flow<Long> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[Keys.GOAL_CELEBRATED_DAY] ?: -1L }

    suspend fun setGoalCelebrated(epochDay: Long) {
        dataStore.edit { it[Keys.GOAL_CELEBRATED_DAY] = epochDay }
    }

    /**
     * Marks [epochDay] as notified. Returns true only for the first call that day, so two
     * step saves arriving together can never show the notification twice.
     */
    suspend fun markGoalNotified(epochDay: Long): Boolean {
        var first = false
        dataStore.edit { p ->
            first = p[Keys.GOAL_NOTIFIED_DAY] != epochDay
            p[Keys.GOAL_NOTIFIED_DAY] = epochDay
        }
        return first
    }

    suspend fun saveAccent(argb: Int) {
        dataStore.edit { it[Keys.ACCENT] = argb }
    }
}
