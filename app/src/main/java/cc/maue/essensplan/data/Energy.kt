package cc.maue.essensplan.data

import java.time.LocalDate

/**
 * Kalorienbedarf pro Tag.
 * Grundumsatz: mit Körperfett Katch-McArdle (genauer), sonst Mifflin-St Jeor.
 * Dazu Alltag (Garmin-Schnitt ohne Sporttage) und geplante Einheiten aus dem Kalender.
 */
object Energy {

    data class DayTarget(
        val day: LocalDate, val kcal: Int, val proteinG: Int, val note: String,
        val bmr: Int = 0, val everyday: Int = 0, val sport: Int = 0, val extra: Int = 0, val how: String = "",
    )

    fun bmr(weightKg: Double, bodyFatPct: Double?, heightCm: Double, age: Int, male: Boolean): Double =
        if (bodyFatPct != null) 370 + 21.6 * weightKg * (1 - bodyFatPct / 100)
        else 10 * weightKg + 6.25 * heightCm - 5 * age + if (male) 5 else -161

    /** Grobe kcal pro Minute nach Art der Einheit (CrossFit als Standard). */
    private fun kcalPerMin(title: String): Double {
        val t = title.lowercase()
        return when {
            "lauf" in t || "run" in t -> 11.0
            "rad" in t || "bike" in t -> 9.0
            "yoga" in t || "mobility" in t -> 3.5
            "schwimm" in t || "swim" in t -> 9.0
            else -> 10.0 // CrossFit / WOD / Gym
        }
    }

    fun targets(
        days: List<LocalDate>,
        events: List<CalendarRepo.Event>,
        health: HealthRepo.Snapshot,
        settings: Settings,
    ): List<DayTarget> {
        val weight = health.weightKg ?: 80.0
        val height = health.heightCm ?: settings.heightCm.toDoubleOrNull() ?: 180.0
        val age = LocalDate.now().year - (settings.birthYear.toIntOrNull() ?: 1996)
        val base = bmr(weight, health.bodyFatPct, height, age, settings.sex != "w")
        // Alltagsbewegung: Garmin-Schnitt, gedeckelt, damit Sporttage nicht doppelt zählen
        val everyday = (health.avgActiveKcal14d ?: (base * 0.35)).coerceAtMost(base * 0.5) * 0.6
        val leanMass = health.bodyFatPct?.let { weight * (1 - it / 100) } ?: (weight * 0.82)

        return days.map { d ->
            val today = events.filter { it.day == d }
            val sport = today.filter { it.isSport && !it.allDay }
            val sportKcal = sport.sumOf { it.minutes * kcalPerMin(it.title) }
            val busyAllDay = today.any { it.allDay && !it.isSport }
            val kcal = base + everyday + sportKcal + (if (busyAllDay) 150 else 0)
            val protein = (leanMass * if (sport.isNotEmpty()) 2.4 else 2.0).toInt()
            val note = buildString {
                if (sport.isNotEmpty()) append("Sport: " + sport.joinToString { "${it.title} ${it.minutes} min" })
                val other = today.filter { !it.isSport }.map { it.title }
                if (other.isNotEmpty()) { if (isNotEmpty()) append(" | "); append("Termine: " + other.joinToString()) }
            }
            val formula = if (health.bodyFatPct != null)
                "Grundumsatz nach Katch-McArdle: 370 + 21,6 × Magermasse %.1f kg".format(leanMass)
            else "Grundumsatz nach Mifflin-St Jeor (kein Körperfett): Gewicht %.1f kg, Größe %.0f cm, Alter $age".format(weight, height)
            val how = buildString {
                append(formula)
                append("\nAlltag: " + (if (health.avgActiveKcal14d != null) "60 %% vom Garmin-Schnitt %d kcal (max. halber Grundumsatz)".format(health.avgActiveKcal14d.toInt()) else "geschätzt, keine Garmin-Daten"))
                if (sport.isNotEmpty()) append("\nSport: " + sport.joinToString { "${it.title} ${it.minutes} min × %.1f kcal/min".format(kcalPerMin(it.title)) })
                if (busyAllDay) append("\n+150 kcal für ganztägigen Termin")
                append("\nProtein: Magermasse × " + (if (sport.isNotEmpty()) "2,4" else "2,0") + " g")
                if (health.weightKg == null) append("\nAchtung: kein Gewicht gefunden, 80 kg angenommen")
            }
            DayTarget(d, kcal.toInt(), protein, note, base.toInt(), everyday.toInt(), sportKcal.toInt(), if (busyAllDay) 150 else 0, how)
        }
    }
}
