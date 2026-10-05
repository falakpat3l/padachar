package com.falakpatel.stridelocal.data

import com.falakpatel.stridelocal.sensor.DayClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

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

    /** Emits today's epoch day now and again right after each local midnight. */
    private fun todayTicker(): Flow<Long> = flow {
        while (true) {
            emit(DayClock.today())
            delay(DayClock.millisUntilNextMidnight() + 1_000)
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeToday(): Flow<DailySteps> = todayTicker().flatMapLatest { day ->
        dao.observeDay(day).map { it ?: DailySteps(day) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeFoodToday(): Flow<List<FoodEntry>> = todayTicker().flatMapLatest { dao.observeFood(it) }

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
