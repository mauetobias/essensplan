package cc.maue.essensplan.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant

/**
 * Liest Waage (MyBeo über AFit Health) und Garmin (über Garmin Connect)
 * aus Google Health Connect. Beide Apps müssen in Health Connect als
 * Datenquelle freigegeben sein (siehe README).
 */
class HealthRepo(context: Context) {

    val available = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    companion object {
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
            HealthPermission.getReadPermission(LeanBodyMassRecord::class),
            HealthPermission.getReadPermission(HeightRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        )
    }

    suspend fun hasAllPermissions() =
        available && client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)

    data class Snapshot(
        val weightKg: Double?,
        val bodyFatPct: Double?,
        val heightCm: Double?,
        val avgActiveKcal14d: Double?,
        val avgSteps14d: Double?,
        val avgSleepH7d: Double?,
        val restingHr: Long?,
        val recentWorkouts: List<String>,
    ) {
        fun asText() = buildString {
            appendLine("Gewicht: ${weightKg?.let { "%.1f kg".format(it) } ?: "unbekannt"}")
            appendLine("Körperfett: ${bodyFatPct?.let { "%.1f %%".format(it) } ?: "unbekannt"}")
            appendLine("Ø aktive kcal/Tag (14 T): ${avgActiveKcal14d?.toInt() ?: "unbekannt"}")
            appendLine("Ø Schritte/Tag (14 T): ${avgSteps14d?.toInt() ?: "unbekannt"}")
            appendLine("Ø Schlaf (7 T): ${avgSleepH7d?.let { "%.1f h".format(it) } ?: "unbekannt"}")
            appendLine("Ruhepuls: ${restingHr ?: "unbekannt"}")
            if (recentWorkouts.isNotEmpty()) appendLine("Letzte Einheiten: ${recentWorkouts.joinToString("; ")}")
        }
    }

    suspend fun snapshot(): Snapshot {
        if (!available || !hasAllPermissions()) return Snapshot(null, null, null, null, null, null, null, emptyList())
        val now = Instant.now()
        fun range(days: Long) = TimeRangeFilter.between(now.minus(Duration.ofDays(days)), now)

        val weight = latest<WeightRecord>(range(60))?.weight?.inKilograms
        val fat = latest<BodyFatRecord>(range(60))?.percentage?.value
        val height = latest<HeightRecord>(range(3650))?.height?.inMeters?.times(100)
        val rhr = latest<RestingHeartRateRecord>(range(7))?.beatsPerMinute

        val agg = client.aggregate(
            AggregateRequest(
                setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, StepsRecord.COUNT_TOTAL),
                range(14),
            )
        )
        val activeKcal = agg[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.div(14)
        val steps = agg[StepsRecord.COUNT_TOTAL]?.toDouble()?.div(14)

        val sleep = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range(7))).records
        val sleepH = sleep.takeIf { it.isNotEmpty() }
            ?.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }?.div(60.0)?.div(7)

        val workouts = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range(7))).records
            .takeLast(6)
            .map { "${it.title ?: "Training"} (${Duration.between(it.startTime, it.endTime).toMinutes()} min)" }

        return Snapshot(weight, fat, height, activeKcal, steps, sleepH, rhr, workouts)
    }

    private suspend inline fun <reified T : Record> latest(filter: TimeRangeFilter): T? =
        client.readRecords(ReadRecordsRequest(T::class, filter, ascendingOrder = false, pageSize = 1))
            .records.firstOrNull()
}
