package cc.maue.essensplan.data

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Holt alle Daten, lässt die KI planen und schiebt die Einkaufsliste nach Bring. */
class PlanGenerator(private val context: Context) {

    data class Output(val markdown: String, val bringAdded: Int, val provider: String, val warnings: List<String>)

    suspend fun run(from: LocalDate, to: LocalDate, sendToBring: Boolean, progress: (String) -> Unit): Output {
        val settings = Settings(context)
        val warnings = mutableListOf<String>()
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()

        progress("Lese Waage und Garmin …")
        val health = runCatching { HealthRepo(context).snapshot() }
            .onFailure { warnings += "Health Connect: ${it.message}" }
            .getOrElse { HealthRepo.Snapshot(null, null, null, null, null, null, null, emptyList()) }
        if (health.weightKg == null) warnings += "Kein Gewicht in Health Connect gefunden, nutze Schätzwert."

        progress("Lese Kalender …")
        val events = runCatching {
            CalendarRepo(context).events(from, to, settings.sportKeywords.split(","))
        }.onFailure { warnings += "Kalender: ${it.message}" }.getOrElse { emptyList() }

        val targets = Energy.targets(days, events, health, settings)

        progress("Lade Rezepte aus YAZIO …")
        val recipes = runCatching { YazioClient(settings.yazioEmail, settings.yazioPassword).recipes() }
            .onFailure { warnings += "YAZIO: ${it.message}" }.getOrElse { emptyList() }

        progress("Erstelle Plan …")
        val fmt = DateTimeFormatter.ofPattern("dd.MM.")
        val dayLines = targets.joinToString("\n") { t ->
            val wd = t.day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN)
            "- $wd ${t.day.format(fmt)}: ${t.kcal} kcal, ${t.proteinG} g Protein" + (if (t.note.isNotBlank()) " (${t.note})" else "")
        }
        val recipeText = if (recipes.isEmpty()) "Keine Rezepte verfügbar, schlage einfache eigene Gerichte vor." else
            recipes.joinToString("\n") { r ->
                "- ${r.name}: ${r.kcal ?: "?"} kcal, ${r.proteinG ?: "?"} g Protein pro Portion; Zutaten (${r.servings} Portionen): ${r.ingredients.joinToString(", ")}"
            }

        val system = """
            Du bist ein Ernährungsplaner. Antworte ausschließlich mit gültigem JSON, ohne Markdown-Codeblock.
            Schreibe auf Deutsch und benutze keine Gedankenstriche.
        """.trimIndent()

        val user = """
            Erstelle einen Essensplan und eine Einkaufsliste für ${days.size} Tag(e).

            Körperdaten:
            ${health.asText()}
            Ziel: ${settings.goal}
            Nicht gewünscht: ${settings.dislikes}

            Tagesziele (bereits aus Grundumsatz, Garmin und Kalender berechnet, bitte einhalten, ±10 %):
            $dayLines

            Rezepte aus YAZIO (bevorzugt verwenden, gern Reste am Folgetag einplanen):
            $recipeText

            Regeln:
            - An Sporttagen Kohlenhydrate vor und Protein nach der Einheit einplanen; Termine im Tagesverlauf berücksichtigen (z.B. Messe: mitnehmbare Mahlzeiten).
            - Einkaufsliste zusammenfassen, in haushaltsüblichen Mengen, nur für den Zeitraum; Grundvorräte (Salz, Öl, Gewürze) weglassen.
            - Bei nur 1 bis 2 Tagen: kleine Mengen, nichts was verdirbt.

            JSON-Format:
            {"tage":[{"datum":"TT.MM.","kcal_ziel":0,"mahlzeiten":[{"name":"","uhrzeit":"","rezept":"","kcal":0,"protein_g":0}]}],
             "einkauf":[{"artikel":"","menge":""}],
             "hinweise":""}
        """.trimIndent()

        val result = LlmClient(settings).complete(system, user)
        val plan = JSONObject(result.text.substringAfter("{").let { "{$it" }.substringBeforeLast("}") + "}")

        val shopping = plan.optJSONArray("einkauf")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it).let { o -> o.optString("artikel") to o.optString("menge") } }
        }.orEmpty().filter { it.first.isNotBlank() }

        var added = 0
        if (sendToBring && shopping.isNotEmpty()) {
            progress("Übertrage ${shopping.size} Artikel nach Bring …")
            added = runCatching { BringClient(settings.bringEmail, settings.bringPassword).addItems(shopping) }
                .onFailure { warnings += "Bring: ${it.message}" }.getOrDefault(0)
        }

        val md = toMarkdown(plan, shopping)
        settings.lastPlan = md
        settings.lastPlanTitle = "${from.format(fmt)} bis ${to.format(fmt)}"
        return Output(md, added, result.provider, warnings)
    }

    private fun toMarkdown(plan: JSONObject, shopping: List<Pair<String, String>>) = buildString {
        val tage = plan.optJSONArray("tage")
        if (tage != null) for (i in 0 until tage.length()) {
            val t = tage.getJSONObject(i)
            appendLine("${t.optString("datum")}  (Ziel ${t.optInt("kcal_ziel")} kcal)")
            val m = t.optJSONArray("mahlzeiten") ?: continue
            for (j in 0 until m.length()) {
                val x = m.getJSONObject(j)
                appendLine("  ${x.optString("uhrzeit")}  ${x.optString("name")}: ${x.optString("rezept")} · ${x.optInt("kcal")} kcal · ${x.optInt("protein_g")} g P")
            }
            appendLine()
        }
        appendLine("Einkauf:")
        shopping.forEach { appendLine("  • ${it.first} ${it.second}") }
        plan.optString("hinweise").takeIf { it.isNotBlank() }?.let { appendLine(); appendLine(it) }
    }
}
