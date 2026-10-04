package com.falakpatel.stridelocal.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.falakpatel.stridelocal.health.HealthMetrics
import com.falakpatel.stridelocal.strideApp
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.Period

/**
 * One-time, read-only import of past days from Health Connect, the on-device health store that
 * Google Fit / Google Health (and Samsung Health etc.) sync into. No internet is involved.
 *
 * Only full days before today are imported (today is counted live by this app). For each day
 * we keep whichever total is higher, so importing twice never double counts.
 */
object HealthConnectImport {
    val STEPS = HealthPermission.getReadPermission(StepsRecord::class)
    val PERMISSIONS = setOf(
        STEPS,
        HealthPermission.getReadPermission(DistanceRecord::class),
        // Without this, Health Connect only shares the 30 days before permission was granted.
        "android.permission.health.READ_HEALTH_DATA_HISTORY",
    )

    fun status(ctx: Context): Int = HealthConnectClient.getSdkStatus(ctx)

    suspend fun hasPermission(ctx: Context): Boolean =
        STEPS in HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions()

    /** @return number of days added or raised. */
    suspend fun importAll(ctx: Context, yearsBack: Long = 10): Int {
        val client = HealthConnectClient.getOrCreate(ctx)
        val app = ctx.strideApp
        val p = app.userPreferences.profile.first()
        val earliest = LocalDate.now().minusYears(yearsBack)
        var end = LocalDate.now()
        var changed = 0
        while (end > earliest) {
            val start = maxOf(earliest, end.minusDays(180)) // 180 one-day buckets per request
            val buckets = try {
                client.aggregateGroupByPeriod(
                    AggregateGroupByPeriodRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL, DistanceRecord.DISTANCE_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(start.atStartOfDay(), end.atStartOfDay()),
                        timeRangeSlicer = Period.ofDays(1),
                    ),
                )
            } catch (e: SecurityException) {
                break // history permission not granted: older data is not readable
            }
            val rows = buckets.mapNotNull { b ->
                val steps = b.result[StepsRecord.COUNT_TOTAL]?.takeIf { it > 0 } ?: return@mapNotNull null
                val day = b.startTime.toLocalDate().toEpochDay()
                val old = app.stepRepository.getDay(day)
                if (old != null && old.steps >= steps) return@mapNotNull null
                DailySteps(
                    epochDay = day,
                    steps = steps,
                    distanceKm = b.result[DistanceRecord.DISTANCE_TOTAL]?.inKilometers
                        ?: HealthMetrics.distanceKm(steps, p.strideM),
                    activeKcal = HealthMetrics.kcalForSteps(steps, 100.0, p.strideM, p.weightKg),
                    goal = old?.goal ?: p.dailyGoal,
                )
            }
            app.stepRepository.upsertDays(rows)
            changed += rows.size
            end = start
        }
        return changed
    }
}
