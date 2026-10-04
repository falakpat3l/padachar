package com.falakpatel.stridelocal.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One row per local calendar day (LocalDate.toEpochDay()). */
@Entity(tableName = "daily_steps")
data class DailySteps(
    @PrimaryKey val epochDay: Long,
    val steps: Long = 0,
    val distanceKm: Double = 0.0,
    val activeKcal: Double = 0.0,
    val goal: Int = 8_000,
)

/** Single row holding the last raw hardware counter value and the boot it came from. */
@Entity(tableName = "tracker_state")
data class TrackerState(
    @PrimaryKey val id: Int = 0,
    val lastCounter: Long = -1,
    val bootCount: Int = -1,
)

@Dao
abstract class StepDao {
    @Query("SELECT * FROM daily_steps WHERE epochDay = :day")
    abstract fun observeDay(day: Long): Flow<DailySteps?>

    @Query("SELECT * FROM daily_steps WHERE epochDay = :day")
    abstract suspend fun getDay(day: Long): DailySteps?

    @Query("SELECT * FROM daily_steps WHERE epochDay >= :fromDay ORDER BY epochDay")
    abstract fun observeFrom(fromDay: Long): Flow<List<DailySteps>>

    @Query("SELECT * FROM tracker_state WHERE id = 0")
    abstract suspend fun getState(): TrackerState?

    @Upsert
    abstract suspend fun upsertDay(day: DailySteps)

    @Upsert
    abstract suspend fun upsertState(state: TrackerState)

    /**
     * Counter baseline and step totals are written in ONE transaction. If they were written
     * separately, a crash in between could make the next delta count steps twice.
     */
    @Transaction
    open suspend fun saveSnapshot(state: TrackerState, days: List<DailySteps>) {
        upsertState(state)
        days.forEach { upsertDay(it) }
    }
}

@Database(entities = [DailySteps::class, TrackerState::class], version = 1, exportSchema = false)
abstract class StepDatabase : RoomDatabase() {
    abstract fun stepDao(): StepDao

    companion object {
        fun build(context: Context): StepDatabase =
            Room.databaseBuilder(context, StepDatabase::class.java, "steps.db").build()
    }
}
