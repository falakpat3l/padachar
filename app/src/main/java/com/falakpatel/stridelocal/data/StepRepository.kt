package com.falakpatel.stridelocal.data

import com.falakpatel.stridelocal.sensor.DayClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Local-only storage (steps and food). Rows are kept forever until the user deletes them. */
class StepRepository(private val dao: StepDao) {

    suspend fun loadState(): TrackerState = dao.getState() ?: TrackerState()
    suspend fun getDay(day: Long): DailySteps? = dao.getDay(day)
    suspend fun today(): DailySteps = DayClock.today().let { dao.getDay(it) ?: DailySteps(it) }
    suspend fun save(state: TrackerState, days: List<DailySteps>) = dao.saveSnapshot(state, days)
    suspend fun upsertDays(days: List<DailySteps>) = dao.upsertDays(days)
    suspend fun deleteDay(day: Long) = dao.deleteWholeDay(day)
    suspend fun deleteOldest(n: Int) = dao.deleteOldestDays(n)
    suspend fun allDays(): List<DailySteps> = dao.allDays()
    suspend fun allFood(): List<FoodEntry> = dao.allFood()
    suspend fun addFood(entry: FoodEntry) = dao.insertFood(entry)
    suspend fun deleteFood(id: Long) = dao.deleteFood(id)
    suspend fun hasFood(timeMs: Long, name: String): Boolean = dao.countFood(timeMs, name) > 0
    fun observeAll(): Flow<List<DailySteps>> = dao.observeAll()

    /** Bumped by [checkDay] so every open screen looks at the date again. */
    private val dayCheck = MutableStateFlow(0L)

    /**
     * Ask open screens to look at the date again now. Called after midnight and clock or time
     * zone changes, because the timer below pauses while the phone sleeps and can end late.
     */
    fun checkDay() = dayCheck.update { it + 1 }

    /** Emits today's epoch day now, again right after each local midnight, and on [checkDay]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun todayTicker(): Flow<Long> = dayCheck.flatMapLatest {
        flow {
            while (true) {
                emit(DayClock.today())
                delay(DayClock.millisUntilNextMidnight() + 1_000)
            }
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeToday(): Flow<DailySteps> = todayTicker().flatMapLatest { day ->
        dao.observeDay(day).map { it ?: DailySteps(day) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeFoodToday(): Flow<List<FoodEntry>> = todayTicker().flatMapLatest { dao.observeFood(it) }

    /** Every day from [fromDay] to [toDay] (epoch days, both included), empty days filled in as zero. */
    fun observeRange(fromDay: Long, toDay: Long): Flow<List<DailySteps>> =
        dao.observeBetween(fromDay, toDay).map { rows ->
            val byDay = rows.associateBy { it.epochDay }
            (fromDay..toDay).map { byDay[it] ?: DailySteps(it) }
        }

    /** Last [days] days including today, with empty days filled in as zero. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeHistory(days: Int): Flow<List<DailySteps>> = todayTicker().flatMapLatest { today ->
        val from = today - days + 1
        dao.observeFrom(from).map { rows ->
            val byDay = rows.associateBy { it.epochDay }
            (from..today).map { byDay[it] ?: DailySteps(it) }
        }
    }
}
