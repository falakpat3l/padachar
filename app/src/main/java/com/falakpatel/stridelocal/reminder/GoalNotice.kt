package com.falakpatel.stridelocal.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.falakpatel.stridelocal.MainActivity
import com.falakpatel.stridelocal.R
import com.falakpatel.stridelocal.health.GoalProgress
import com.falakpatel.stridelocal.strideApp
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * One "Daily goal reached" notification a day, shown when the counting service saves the
 * steps that cross your goal. Uses the same notification permission as the move reminder and
 * never asks for it by itself. Can be switched off in Settings.
 */
object GoalNotice {
    private const val CHANNEL_ID = "daily_goal"
    private const val NOTIFICATION_ID = 3

    suspend fun maybeNotify(ctx: Context) {
        val app = ctx.strideApp
        if (!MoveReminder.hasPermission(app) || !app.userPreferences.goalNotice.first()) return
        val day = app.stepRepository.today()
        val goal = app.userPreferences.profile.first().dailyGoal
        if (!GoalProgress.reached(day.steps, goal)) return
        if (!app.userPreferences.markGoalNotified(day.epochDay)) return // already shown today

        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Daily goal", NotificationManager.IMPORTANCE_DEFAULT).apply { setShowBadge(false) },
        )
        val open = PendingIntent.getActivity(
            app, 1, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_steps)
                .setContentTitle("Daily goal reached")
                .setContentText(String.format(Locale.getDefault(), "%,d steps today. Well done.", day.steps))
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build(),
        )
    }
}
