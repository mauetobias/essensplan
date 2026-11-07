package cc.maue.essensplan.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Alles, was in einen Plan einfließt, als nachvollziehbares Protokoll für den Daten-Tab.
 * Wird bei jedem Plan gespeichert und kann im Daten-Tab live neu erzeugt werden.
 */
object Inputs {

    fun build(
        context: Context, health: HealthRepo.Snapshot, events: List<CalendarRepo.Event>, targets: List<Energy.DayTarget>,
        settings: Settings, yazioStatus: String = "", model: String = "",
    ): JSONObject {
        fun f(v: Double?, fmt: String) = v?.let { fmt.format(it) } ?: "fehlt"
        val body = JSONArray()
            .put(row("Gewicht", f(health.weightKg, "%.1f kg"), health.weightInfo))
            .put(row("Körperfett", f(health.bodyFatPct, "%.1f %%"), health.fatInfo))
            .put(row("Größe", f(health.heightCm ?: settings.heightCm.toDoubleOrNull(), "%.0f cm"),
                if (health.heightCm != null) "aus Health Connect" else "aus deinen Einstellungen"))
            .put(row("Alter", "${LocalDate.now().year - (settings.birthYear.toIntOrNull() ?: 1996)} Jahre", "aus Geburtsjahr"))
        val activity = JSONArray()
            .put(row("Ø aktive kcal", f(health.avgActiveKcal14d, "%.0f kcal/Tag"), "Garmin, nur Tage mit Daten"))
            .put(row("Ø Schritte", f(health.avgSteps14d, "%.0f /Tag"), "Garmin, nur Tage mit Daten"))
            .put(row("Ø Schlaf", f(health.avgSleepH7d, "%.1f h/Nacht"), "${health.sleepNights} Nächte in 7 Tagen"))
            .put(row("Ruhepuls", health.restingHr?.let { "$it bpm" } ?: "fehlt", "letzte 7 Tage"))

        val dfmt = DateTimeFormatter.ofPattern("dd.MM.")
        return JSONObject()
            .put("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM. HH:mm")))
            .put("body", body)
            .put("activity", activity)
            .put("daily", JSONArray(health.daily))
            .put("workouts", JSONArray(health.recentWorkouts))
            .put("events", JSONArray().apply {
                events.forEach {
                    put(JSONObject().put("day", it.day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN) + " " + it.day.format(dfmt))
                        .put("title", it.title).put("min", if (it.allDay) "ganztägig" else "${it.minutes} min").put("sport", it.isSport))
                }
            })
            .put("targets", JSONArray().apply {
                targets.forEach {
                    put(JSONObject().put("day", it.day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN) + ", " + it.day.format(dfmt))
                        .put("kcal", it.kcal).put("protein", it.proteinG).put("bmr", it.bmr).put("everyday", it.everyday)
                        .put("sport", it.sport).put("extra", it.extra).put("how", it.how))
                }
            })
            .put("yazio", yazioStatus.ifBlank { "${RecipeBook(context).all().size} Rezepte im lokalen Rezeptbuch" })
            .put("ebons", PurchaseHistory(context).let { "${it.receiptCount()} eBons gelernt. Häufig: " + it.top(12).joinToString { p -> "${p.first} (${p.second}x)" } })
            .put("pantry", Pantry(context).describe())
            .put("settings", JSONArray()
                .put(row("Ziel", settings.goal, ""))
                .put(row("Rhythmus", settings.rhythm, ""))
                .put(row("Mag nicht", settings.dislikes, ""))
                .put(row("Sport-Stichwörter", settings.sportKeywords, "so erkennt die App Training im Kalender")))
            .put("model", model.ifBlank { "Gemini ${settings.geminiModel}" })
    }

    private fun row(label: String, value: String, info: String) = JSONObject().put("l", label).put("v", value).put("i", info)
}
