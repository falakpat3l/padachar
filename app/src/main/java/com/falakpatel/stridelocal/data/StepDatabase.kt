package com.falakpatel.stridelocal.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** One row per local calendar day (LocalDate.toEpochDay()). */
@Entity(tableName = "daily_steps")
data class DailySteps(
    @PrimaryKey val epochDay: Long,
    val steps: Long = 0,
    val distanceKm: Double = 0.0,
    val activeKcal: Double = 0.0,
    val goal: Int = 8_000,
    @ColumnInfo(defaultValue = "0") val walkMin: Int = 0,
    @ColumnInfo(defaultValue = "0") val runMin: Int = 0,
    @ColumnInfo(defaultValue = "0") val runSteps: Long = 0,
) {
    val walkSteps: Long get() = (steps - runSteps).coerceAtLeast(0)
}

/** One food item eaten. kcal and macros are totals (already multiplied by servings). */
@Entity(tableName = "food_entries")
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val timeMs: Long,
    val name: String,
    val servings: Double,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

/** Single row holding the last raw hardware counter value and the boot it came from. */
@Entity(tableName = "tracker_state")
data class TrackerState(
    @PrimaryKey val id: Int = 0,
    val lastCounter: Long = -1,
    val bootCount: Int = -1,
    /** Wall time of the last saved reading, used for pace and midnight splitting. */
    @ColumnInfo(defaultValue = "0") val lastSyncMs: Long = 0,
)

@Dao
abstract class StepDao {
    @Query("SELECT * FROM daily_steps WHERE epochDay = :day")
    abstract fun observeDay(day: Long): Flow<DailySteps?>

    @Query("SELECT * FROM daily_steps WHERE epochDay = :day")
    abstract suspend fun getDay(day: Long): DailySteps?

    @Query("SELECT * FROM daily_steps WHERE epochDay >= :fromDay ORDER BY epochDay")
    abstract fun observeFrom(fromDay: Long): Flow<List<DailySteps>>

    @Query("SELECT * FROM daily_steps WHERE epochDay BETWEEN :fromDay AND :toDay ORDER BY epochDay")
    abstract fun observeBetween(fromDay: Long, toDay: Long): Flow<List<DailySteps>>

    @Query("SELECT * FROM daily_steps ORDER BY epochDay DESC")
    abstract fun observeAll(): Flow<List<DailySteps>>

    @Query("DELETE FROM daily_steps WHERE epochDay = :day")
    abstract suspend fun deleteStepsDay(day: Long)

    @Query("DELETE FROM food_entries WHERE epochDay = :day")
    abstract suspend fun deleteFoodDay(day: Long)

    @Query("SELECT epochDay FROM daily_steps ORDER BY epochDay ASC LIMIT 1 OFFSET :offset")
    abstract suspend fun nthOldestDay(offset: Int): Long?

    @Query("DELETE FROM daily_steps WHERE epochDay <= :day")
    abstract suspend fun deleteStepsUpTo(day: Long)

    @Query("DELETE FROM food_entries WHERE epochDay <= :day")
    abstract suspend fun deleteFoodUpTo(day: Long)

    /** Deletes everything (steps and food) for one day. */
    @Transaction
    open suspend fun deleteWholeDay(day: Long) {
        deleteStepsDay(day)
        deleteFoodDay(day)
    }

    /** Deletes the [n] oldest stored days, steps and food together. */
    @Transaction
    open suspend fun deleteOldestDays(n: Int) {
        val cutoff = nthOldestDay(n - 1) ?: return
        deleteStepsUpTo(cutoff)
        deleteFoodUpTo(cutoff)
    }

    @Query("SELECT * FROM daily_steps ORDER BY epochDay")
    abstract suspend fun allDays(): List<DailySteps>

    @Query("SELECT * FROM food_entries WHERE epochDay = :day ORDER BY timeMs DESC")
    abstract fun observeFood(day: Long): Flow<List<FoodEntry>>

    @Query("SELECT * FROM food_entries ORDER BY timeMs")
    abstract suspend fun allFood(): List<FoodEntry>

    @Query("SELECT COUNT(*) FROM food_entries WHERE timeMs = :timeMs AND name = :name")
    abstract suspend fun countFood(timeMs: Long, name: String): Int

    @Insert
    abstract suspend fun insertFood(entry: FoodEntry)

    @Query("DELETE FROM food_entries WHERE id = :id")
    abstract suspend fun deleteFood(id: Long)

    @Upsert
    abstract suspend fun upsertDays(days: List<DailySteps>)

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

@Database(entities = [DailySteps::class, TrackerState::class, FoodEntry::class], version = 4, exportSchema = false)
abstract class StepDatabase : RoomDatabase() {
    abstract fun stepDao(): StepDao

    companion object {
        fun build(context: Context): StepDatabase =
            Room.databaseBuilder(context, StepDatabase::class.java, "steps.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracker_state ADD COLUMN lastSyncMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `food_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, `timeMs` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                        "`servings` REAL NOT NULL, `kcal` REAL NOT NULL, `proteinG` REAL NOT NULL, " +
                        "`carbsG` REAL NOT NULL, `fatG` REAL NOT NULL)",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_steps ADD COLUMN walkMin INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_steps ADD COLUMN runMin INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_steps ADD COLUMN runSteps INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
