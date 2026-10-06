package com.falakpatel.stridelocal.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.falakpatel.stridelocal.MainActivity
import com.falakpatel.stridelocal.R
import com.falakpatel.stridelocal.sensor.DayClock
import com.falakpatel.stridelocal.strideApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Optional "time to move" nudge. Uses one inexact alarm at a time (no exact-alarm permission,
 * no background work between alarms), so it costs almost no battery.
 * The notification is silent, never pops up, and disappears by itself after 15 minutes.
 */
object MoveReminder {
    private const val CHANNEL_ID = "move_reminders"
    private const val NOTIFICATION_ID = 2

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun scheduleNext(ctx: Context) {
        val at = ReminderSchedule.next(System.currentTimeMillis())
        ctx.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(ctx))
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx))
        ctx.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun alarmIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 7, Intent(ctx, MoveReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Shows the nudge unless it is outside 7:00 to 21:00 or you already walked since the last check. */
    suspend fun maybeNotify(ctx: Context) {
        val now = System.currentTimeMillis()
        if (!ReminderSchedule.inWindow(now) || !hasPermission(ctx)) return
        val today = DayClock.epochDay(now)
        val steps = ctx.strideApp.stepRepository.today().steps
        val sp = ctx.getSharedPreferences("move_reminder", Context.MODE_PRIVATE)
        val sameDay = sp.getLong("day", -1) == today
        val before = sp.getLong("steps", 0)
        sp.edit().putLong("day", today).putLong("steps", steps).apply()
        if (!ReminderSchedule.shouldRemind(steps, before, sameDay)) return

        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Move reminders", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_steps)
                .setContentTitle("Time to move")
                .setContentText("A short walk?")
                .setSilent(true)
                .setAutoCancel(true)
                .setTimeoutAfter(15 * 60_000L)
                .setContentIntent(open)
                .build(),
        )
    }
}

/** Fires at each scheduled time: maybe nudge, then plan the next one. */
class MoveReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.strideApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                if (app.userPreferences.moveReminders.first()) {
                    MoveReminder.maybeNotify(app)
                    MoveReminder.scheduleNext(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
