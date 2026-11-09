package com.falakpatel.stridelocal.reminder

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.falakpatel.stridelocal.MainActivity
import com.falakpatel.stridelocal.R
import com.falakpatel.stridelocal.health.CycleMath
import com.falakpatel.stridelocal.sensor.DayClock
import com.falakpatel.stridelocal.strideApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Optional "period may start tomorrow" notification, at about 9 am the day before the
 * estimated start. One inexact alarm, planned again whenever the log changes, after a
 * restart and after midnight, so it follows the latest estimate.
 * On the lock screen it only says "padachar reminder", so nobody else can read it there.
 */
object CycleReminder {
    private const val CHANNEL_ID = "cycle_reminder"
    private const val NOTIFICATION_ID = 4
    private val AT = LocalTime.of(9, 0)
    private const val STATE = "cycle_reminder"

    private fun notifiedDay(ctx: Context): Long =
        ctx.getSharedPreferences(STATE, Context.MODE_PRIVATE).getLong("day", -1L)

    /** Plans (or cancels) the alarm from the current log and settings. Safe to call often. */
    suspend fun reschedule(ctx: Context) {
        val app = ctx.strideApp
        val prefs = app.userPreferences
        val on = prefs.cycleEnabled.first() && prefs.cycleReminder.first()
        val today = DayClock.today()
        val day = if (on) CycleMath.reminderDay(CycleMath.summary(prefs.cycleDays.first(), today), today) else null
        val am = app.getSystemService(AlarmManager::class.java)
        if (day == null || (day == today && notifiedDay(app) == today)) {
            am.cancel(alarmIntent(app))
            return
        }
        val zone = ZoneId.systemDefault()
        val at = LocalDate.ofEpochDay(day).atTime(AT).atZone(zone).toInstant().toEpochMilli()
        // Already past 9 am on the reminder day: show it within a minute instead.
        val whenMs = maxOf(at, System.currentTimeMillis() + 60_000L)
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, alarmIntent(app)) }
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx))
        ctx.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun alarmIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 9, Intent(ctx, CycleReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Shows the notification only if, with the log as it is now, today is still the day before. */
    suspend fun maybeNotify(ctx: Context) {
        val app = ctx.strideApp
        val prefs = app.userPreferences
        if (!MoveReminder.hasPermission(app) || !prefs.cycleEnabled.first() || !prefs.cycleReminder.first()) return
        val today = DayClock.today()
        if (CycleMath.reminderDay(CycleMath.summary(prefs.cycleDays.first(), today), today) != today) return
        if (notifiedDay(app) == today) return // once a day, even if the log changes after it showed
        app.getSharedPreferences(STATE, Context.MODE_PRIVATE).edit().putLong("day", today).apply()

        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Cycle reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
        val open = PendingIntent.getActivity(
            app, 2, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // What the lock screen shows instead of the real text.
        val hidden = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_steps)
            .setContentTitle("padachar reminder")
            .build()
        nm.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_steps)
                .setContentTitle("Period may start tomorrow")
                .setContentText("Estimated from your log in padachar.")
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(hidden)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build(),
        )
    }
}

/** Fires on the reminder day: maybe notify. The next alarm is planned when the next period is logged. */
class CycleReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.strideApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                runCatching { CycleReminder.maybeNotify(app) }
            } finally {
                pending.finish()
            }
        }
    }
}
