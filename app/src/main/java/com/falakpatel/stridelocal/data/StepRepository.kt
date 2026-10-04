package com.falakpatel.stridelocal.data

import com.falakpatel.stridelocal.sensor.DayClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** A consistent point-in-time copy of tracker state plus the day rows it affects. */
data class Snapshot(val state: TrackerState, val days: List<DailySteps>, val refreshWidget: Boolean)

class StepRepository(
    private val dao: StepDao,
    appScope: CoroutineScope,
    private val afterSave: suspend (Snapshot) -> Unit,
) {
    /**
     * All writes go through one queue with one consumer, so snapshots land in the exact
     * order the service produced them (an older baseline can never overwrite a newer one).
     */
    private val writes = Channel<Snapshot>(Channel.UNLIMITED)

    init {
        appScope.launch {
            for (snap in writes) {
                runCatching { dao.saveSnapshot(snap.state, snap.days) }
                runCatching { afterSave(snap) }
            }
        }
    }

    fun enqueue(snapshot: Snapshot) {
        writes.trySend(snapshot)
    }

    suspend fun loadState(): TrackerState = dao.getState() ?: TrackerState()
    suspend fun getDay(day: Long): DailySteps? = dao.getDay(day)
    suspend fun today(): DailySteps = DayClock.today().let { dao.getDay(it) ?: DailySteps(it) }

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
