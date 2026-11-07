package cc.maue.essensplan.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Holt alle Daten, lässt die KI planen, gleicht mit dem Vorrat ab, sucht Angebote, schiebt nach Bring. */
class PlanGenerator(private val context: Context) {

    data class Output(val text: String, val bringAdded: Int, val provider: String, val warnings: List<String>)

    suspend fun run(from: LocalDate, to: LocalDate, sendToBring: Boolean, progress: (String) -> Unit): Output {
        val settings = Settings(context)
        val pantry = Pantry(context)
        val warnings = mutableListOf<String>()
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()

        pantry.consumePastDays()

        progress("Lese Waage und Garmin …")
        val health = runCatching { HealthRepo(context).snapshot() }
            .onFailure { warnings += "Health Connect: ${it.message}" }
            .getOrElse { HealthRepo.Snapshot(null, null, null, null, null, null, null, emptyList()) }
            .let { settings.withManualBody(it) }
        if (health.weightKg == null) warnings += "Kein Gewicht gefunden, nutze Schätzwert. Trag es im Daten-Tab ein."

        progress("Lese Kalender …")
        val events = runCatching { CalendarRepo(context).events(from, to, settings.sportKeywords.split(",")) }
            .onFailure { warnings += "Kalender: ${it.message}" }.getOrElse { emptyList() }
        val targets = Energy.targets(days, events, health, settings)

        progress("Lade Rezepte aus YAZIO …")
        // Frisch aus YAZIO laden und ins lokale Rezeptbuch übernehmen; geplant wird immer mit dem Rezeptbuch
        val book = RecipeBook(context)
        val yazioStatus = "${book.all().size} Rezepte im lokalen Rezeptbuch"
        val recipes = book.all()

        progress("Erstelle Plan …")
        val fmt = DateTimeFormatter.ofPattern("dd.MM.")
        val dayLines = targets.joinToString("\n") { t ->
            val wd = t.day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN)
            "- ${t.day} ($wd): ${t.kcal} kcal, ${t.proteinG} g Protein" + (if (t.note.isNotBlank()) " (${t.note})" else "")
        }
        val recipeText = if (recipes.isEmpty()) "Keine Rezepte verfügbar, schlage einfache eigene Gerichte vor." else
            recipes.joinToString("\n") { r ->
                "- ${r.name}: ${r.kcal ?: "?"} kcal, ${r.proteinG ?: "?"} g Protein pro Portion, ${r.minutes ?: "?"} min; Zutaten für ${r.servings} Portionen: ${r.ingredients.joinToString(", ")}"
            }

        val system = "Du bist ein Ernährungsplaner. Antworte ausschließlich mit gültigem JSON, ohne Markdown-Codeblock. Deutsch, keine Gedankenstriche."
        val user = """
            Erstelle den Essensplan für GENAU EINEN Tag: __TAG__.
            Bereits geplant (Reste nutzen, nicht wiederholen): __BISHER__

            Körperdaten:
            ${health.asText()}
            Ziel: ${settings.goal}
            Nicht gewünscht: ${settings.dislikes}
            Getränke: grundsätzlich alles zuckerfrei (Zero). Wasser nur Volvic, dazu grüner und schwarzer Tee, Energy Drinks Zero. Getränke und Snacks NICHT in die Zutaten aufnehmen, die verwaltet die App selbst.
            Snacks: isst gern Snacks (High-Protein-Salamisticks, Proteinriegel, Maiswaffeln mit Salz, süßes und Karamell-Popcorn). Plane täglich 1 bis 2 Snacks daraus als Mahlzeit ein und rechne ihre kcal ins Tagesziel ein.
            Lagerplatz: ${settings.storage}
            Essensrhythmus: ${settings.rhythm}
            Typische Einkäufe laut REWE-Kassenbons (Vorlieben, gern berücksichtigen):
            ${PurchaseHistory(context).summary()}

            Tagesziel (fest berechnet, ±10 % einhalten):
            __ZIEL__

            Zu Hause vorhanden:
            ${pantry.describe()}

            Rezepte aus seinem Rezeptbuch (bevorzugt verwenden, Namen exakt übernehmen, Mengen auf seine Portion umrechnen):
            $recipeText

            Regeln:
            - Vorräte zuerst aufbrauchen, Reste am Folgetag einplanen, Zutaten über mehrere Gerichte teilen, damit wenig übrig bleibt.
            - An Sporttagen Kohlenhydrate vor und Protein nach der Einheit; Termine im Tagesverlauf berücksichtigen.
            - Pro Tag ALLE verbrauchten Zutaten mit Menge als Zahl und Einheit (g, ml, Stück) auflisten, auch Grundvorrat.
            - Mengen in Supermarkt-Größen denken (z.B. 1 Packung Hähnchen = 400 g).

            JSON:
            {"tage":[{"datum":"YYYY-MM-DD",
                      "mahlzeiten":[{"name":"Spätes Frühstück|Snack|Abendessen|…","uhrzeit":"HH:MM","rezept":"Name des Gerichts",
                                     "kcal":0,"protein_g":0,"kh_g":0,"fett_g":0,"dauer_min":0,"portionen":1,
                                     "zutaten":["150 g Magerquark", "…"],
                                     "schritte":["Kurzer, klarer Kochschritt", "…"],
                                     "tipp":""}],
                      "zutaten":[{"name":"","menge":0,"einheit":"g"}]}],
             "hinweise":""}
            Bei Snacks reichen 1 bis 2 Schritte. Schritte so schreiben, dass man direkt danach kochen kann (Temperatur, Zeit, Reihenfolge).
        """.trimIndent()

        // Jeder Tag ein eigener, kleiner KI-Aufruf: schneller, robuster und mit Fortschritt
        val tage = JSONArray()
        val hints = mutableListOf<String>()
        var lastProvider = ""
        val llm = LlmClient(settings)
        val dayLineList = dayLines.lines()
        for ((i, d) in days.withIndex()) {
            progress("Plane Tag ${i + 1} von ${days.size} …")
            val bisher = (0 until tage.length()).joinToString("; ") { k ->
                val t = tage.getJSONObject(k); val m = t.optJSONArray("mahlzeiten") ?: JSONArray()
                t.optString("datum") + ": " + (0 until m.length()).joinToString(", ") { j -> m.getJSONObject(j).optString("rezept") }
            }.ifBlank { "noch nichts" }
            val prompt = user.replace("__TAG__", d.toString()).replace("__BISHER__", bisher)
                .replace("__ZIEL__", dayLineList.getOrElse(i) { "" })
            val res = llm.complete(system, prompt)
            lastProvider = res.provider
            val one = JSONObject("{" + res.text.substringAfter("{").substringBeforeLast("}") + "}")
            val arr = one.optJSONArray("tage") ?: JSONArray()
            for (k in 0 until arr.length()) tage.put(arr.getJSONObject(k).put("datum", d.toString()))
            one.optString("hinweise").takeIf { it.isNotBlank() }?.let { hints += it }
        }
        val result = LlmClient.Result("", lastProvider)
        val plan = JSONObject().put("tage", tage).put("hinweise", hints.joinToString(" "))

        // Verbrauch merken und Gesamtbedarf bilden
        val usage = mutableListOf<Pantry.Usage>()
        for (i in 0 until tage.length()) {
            val t = tage.getJSONObject(i)
            val day = runCatching { LocalDate.parse(t.optString("datum")) }.getOrElse { days.getOrElse(i) { from } }
            val z = t.optJSONArray("zutaten") ?: continue
            for (j in 0 until z.length()) z.getJSONObject(j).let {
                usage += Pantry.Usage(day, it.optString("name"), it.optDouble("menge", 1.0), it.optString("einheit", "Stück"))
            }
        }
        val needed = usage.groupBy { Pantry.norm(it.name) + "|" + it.unit }
            .map { (_, l) -> Triple(l.first().name, l.sumOf { it.qty }, l.first().unit) }
        val shopping = pantry.shoppingNeeds(needed)
        pantry.planUsage(usage)
        settings.lastShopping = JSONArray().apply { shopping.forEach { put(JSONObject().put("n", it.first).put("s", it.second)) } }.toString()

        progress("Vergleiche Angebote …")
        val rec = runCatching { Offers(settings.zip.ifBlank { LocationZip.current(context).orEmpty() }, settings.stores.split(","), settings.preferredStore).recommend(shopping.map { it.first }) }
            .onFailure { warnings += "Angebote: ${it.message}" }.getOrNull()

        var added = 0
        if (sendToBring && shopping.isNotEmpty()) {
            progress("Übertrage ${shopping.size} Artikel nach Bring …")
            added = runCatching { BringClient(settings.bringEmail, settings.bringPassword).addItems(shopping) }
                .onFailure { warnings += "Bring: ${it.message}" }.getOrDefault(0)
        }

        // Strukturiertes Ergebnis für die Oberfläche
        val view = JSONObject()
        val vDays = JSONArray()
        for (i in 0 until tage.length()) {
            val t = tage.getJSONObject(i)
            val d = runCatching { LocalDate.parse(t.optString("datum")) }.getOrNull()
            val target = targets.firstOrNull { it.day == d }
            vDays.put(JSONObject()
                .put("date", d?.toString().orEmpty())
                .put("label", d?.let { it.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN) + ", " + it.format(fmt) } ?: t.optString("datum"))
                .put("kcal", target?.kcal ?: 0).put("protein", target?.proteinG ?: 0).put("note", target?.note.orEmpty())
                .put("meals", t.optJSONArray("mahlzeiten") ?: JSONArray()))
        }
        view.put("days", vDays)
        view.put("shopping", JSONArray().apply { shopping.forEach { put(JSONObject().put("n", it.first).put("s", it.second)) } })
        view.put("rec", rec?.text.orEmpty())
        view.put("hints", plan.optString("hinweise"))
        view.put("provider", result.provider)
        view.put("warnings", JSONArray(warnings))
        val text = view.toString()
        settings.lastPlan = text
        settings.lastPlanTitle = "${from.format(fmt)} bis ${to.format(fmt)}"
        settings.lastInputs = Inputs.build(context, health, events, targets, settings, yazioStatus, result.provider + " " + settings.geminiModel).toString()
        return Output(text, added, result.provider, warnings)
    }

    /** "Einkauf erledigt": letzte Liste in den Vorrat buchen. */
    fun markShoppingDone() {
        val a = JSONArray(Settings(context).lastShopping)
        val bought = (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            val spec = o.optString("s").trim()
            val qty = spec.substringBefore(" ").replace(",", ".").toDoubleOrNull() ?: 1.0
            val unit = spec.substringAfter(" ", "Stück").ifBlank { "Stück" }
            Triple(o.getString("n"), qty, unit)
        }
        Pantry(context).addPurchased(bought)
        // Leer markierter Grundvorrat ist jetzt wieder da
        val p = Pantry(context); val list = p.items()
        bought.forEach { b -> p.find(list, b.first)?.takeIf { it.staple }?.empty = false }
        p.save(list)
        Settings(context).lastShopping = "[]"
    }
}
