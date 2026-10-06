package com.falakpatel.stridelocal.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.falakpatel.stridelocal.reminder.MoveReminder
import com.falakpatel.stridelocal.strideApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restarts tracking after a reboot or an app update.
 * BOOT_COMPLETED is one of the exemptions that allow starting a foreground service from the
 * background, and the "health" type is allowed from boot on Android 14 and 15.
 * The step delta logic handles the counter reset (see StepDelta).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                StepCounterService.start(context)
                // Alarms are wiped on reboot, so plan the next move reminder again if it is on.
                val app = context.strideApp
                val pending = goAsync()
                app.appScope.launch {
                    try {
                        if (app.userPreferences.moveReminders.first()) MoveReminder.scheduleNext(app)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
