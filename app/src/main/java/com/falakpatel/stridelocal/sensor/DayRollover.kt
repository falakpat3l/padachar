package com.falakpatel.stridelocal.sensor

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.falakpatel.stridelocal.reminder.CycleReminder
import com.falakpatel.stridelocal.reminder.MoveReminder
import com.falakpatel.stridelocal.strideApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Moves the widget and any open screen to the new day soon after local midnight, instead of
 * waiting for the next 30 minute widget update. One inexact alarm a day, so no exact alarm
 * permission is needed. A clock or time zone change plans it again for the new midnight.
 */
object DayRollover {
    /** A few seconds after midnight, so "today" has surely changed when it fires. */
    private const val AFTER_MIDNIGHT_MS = 5_000L

    /** Android may deliver the alarm anywhere in this window (10 minutes is its minimum). */
    private const val WINDOW_MS = 10 * 60_000L

    /** Plans the next run. Safe to call often: it replaces the alarm already planned. */
    fun schedule(ctx: Context) {
        val at = DayClock.nextMidnight(System.currentTimeMillis()) + AFTER_MIDNIGHT_MS
        runCatching {
            ctx.getSystemService(AlarmManager::class.java)
                .setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, alarmIntent(ctx))
        }
    }

    private fun alarmIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 8, Intent(ctx, DayRolloverReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Runs after midnight and when the clock or time zone is changed. */
class DayRolloverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.strideApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                // Saves the latest count (split across midnight), moves open screens to the
                // new day and redraws every widget.
                runCatching { StepSync.syncNow(app) }
                // Safety net for move reminders: after midnight the next one is due at 7 to 8 am
                // anyway, so planning it again here only matters if its alarm was lost.
                runCatching { if (app.userPreferences.moveReminders.first()) MoveReminder.scheduleNext(app) }
                // The cycle reminder follows the latest estimate, so plan it again each day.
                runCatching { CycleReminder.reschedule(app) }
            } finally {
                DayRollover.schedule(app)
                pending.finish()
            }
        }
    }
}
