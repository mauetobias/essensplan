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
        val weightInfo: String = "",
        val fatInfo: String = "",
        val daily: List<String> = emptyList(),
        val sleepNights: Int = 0,
    ) {
        fun asText() = buildString {
            appendLine("Gewicht: ${weightKg?.let { "%.1f kg".format(it) } ?: "unbekannt"}")
            appendLine("Körperfett: ${bodyFatPct?.let { "%.1f %%".format(it) } ?: "unbekannt"}")
            appendLine("Ø aktive kcal/Tag (Tage mit Daten, max. 14): ${avgActiveKcal14d?.toInt() ?: "unbekannt"}")
            appendLine("Ø Schritte/Tag (14 T): ${avgSteps14d?.toInt() ?: "unbekannt"}")
            appendLine("Ø Schlaf pro Nacht: ${avgSleepH7d?.let { "%.1f h".format(it) } ?: "unbekannt"}")
            appendLine("Ruhepuls: ${restingHr ?: "unbekannt"}")
            if (recentWorkouts.isNotEmpty()) appendLine("Letzte Einheiten: ${recentWorkouts.joinToString("; ")}")
        }
    }

    suspend fun snapshot(): Snapshot {
        if (!available || !hasAllPermissions()) return Snapshot(null, null, null, null, null, null, null, emptyList())
        val now = Instant.now()
        fun range(days: Long) = TimeRangeFilter.between(now.minus(Duration.ofDays(days)), now)

        val fmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(java.time.ZoneId.systemDefault())
        fun origin(r: Record) = r.metadata.dataOrigin.packageName.let { p ->
            when {
                "garmin" in p -> "Garmin Connect"; "afit" in p || "mybeo" in p -> "AFit Health"; "healthdata" in p -> "Health Connect"
                else -> p
            }
        }
        val wRec = latest<WeightRecord>(range(60))
        val fRec = latest<BodyFatRecord>(range(60))
        val weight = wRec?.weight?.inKilograms
        val fat = fRec?.percentage?.value
        val height = latest<HeightRecord>(range(3650))?.height?.inMeters?.times(100)
        val rhr = latest<RestingHeartRateRecord>(range(7))?.beatsPerMinute

        // Tageswerte der letzten 14 Tage; Durchschnitt nur über Tage, an denen wirklich Daten da sind
        val zone = java.time.ZoneId.systemDefault()
        val startDay = java.time.LocalDate.now().minusDays(13).atStartOfDay()
        val perDay = client.aggregateGroupByPeriod(
            androidx.health.connect.client.request.AggregateGroupByPeriodRequest(
                setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, StepsRecord.COUNT_TOTAL),
                TimeRangeFilter.between(startDay, java.time.LocalDateTime.now()),
                java.time.Period.ofDays(1),
            )
        )
        val kcalDays = perDay.mapNotNull { it.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.takeIf { v -> v > 0 } }
        val stepDays = perDay.mapNotNull { it.result[StepsRecord.COUNT_TOTAL]?.takeIf { v -> v > 0 } }
        val activeKcal = kcalDays.takeIf { it.isNotEmpty() }?.average()
        val steps = stepDays.takeIf { it.isNotEmpty() }?.average()
        val daily = perDay.map {
            "${it.startTime.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("EE dd.MM.", java.util.Locale.GERMAN))}: " +
                "${it.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?.toInt() ?: "–"} aktive kcal, " +
                "${it.result[StepsRecord.COUNT_TOTAL] ?: "–"} Schritte"
        }

        val sleep = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range(7))).records
        val nights = sleep.map { it.endTime.atZone(zone).toLocalDate() }.distinct().size
        val sleepH = sleep.takeIf { it.isNotEmpty() }
            ?.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }?.div(60.0)?.div(nights.coerceAtLeast(1))

        val workouts = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range(7))).records
            .takeLast(6)
            .map { "${fmt.format(it.startTime)} ${it.title ?: "Training"} (${Duration.between(it.startTime, it.endTime).toMinutes()} min, ${origin(it)})" }

        return Snapshot(
            weight, fat, height, activeKcal, steps, sleepH, rhr, workouts,
            weightInfo = wRec?.let { "gemessen ${fmt.format(it.time)}, Quelle ${origin(it)}" } ?: "keine Messung in 60 Tagen",
            fatInfo = fRec?.let { "gemessen ${fmt.format(it.time)}, Quelle ${origin(it)}" } ?: "keine Messung in 60 Tagen",
            daily = daily, sleepNights = nights,
        )
    }

    private suspend inline fun <reified T : Record> latest(filter: TimeRangeFilter): T? =
        client.readRecords(ReadRecordsRequest(T::class, filter, ascendingOrder = false, pageSize = 1))
            .records.firstOrNull()
}
