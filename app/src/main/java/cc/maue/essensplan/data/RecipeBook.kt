package cc.maue.essensplan.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lokales Rezeptbuch. Jedes Rezept, das einmal aus YAZIO geladen wurde, bleibt hier dauerhaft
 * gespeichert (Zutaten, Nährwerte, Zubereitung). Auch nach einer YAZIO-Kündigung bleibt alles da.
 */
class RecipeBook(context: Context) {

    private val prefs = context.getSharedPreferences("recipe_book", Context.MODE_PRIVATE)

    fun all(): List<YazioClient.Recipe> {
        val o = JSONObject(prefs.getString("recipes", "{}")!!)
        return o.keys().asSequence().map { fromJson(o.getJSONObject(it)) }.sortedBy { it.name.lowercase() }.toList()
    }

    /** Neue oder geänderte Rezepte übernehmen, nichts wird gelöscht. Gibt Anzahl neuer Rezepte zurück. */
    fun merge(recipes: List<YazioClient.Recipe>): Int {
        val o = JSONObject(prefs.getString("recipes", "{}")!!)
        var added = 0
        for (r in recipes) {
            if (r.name.isBlank()) continue
            val key = r.name.trim().lowercase()
            if (!o.has(key)) added++
            o.put(key, toJson(r))
        }
        prefs.edit().putString("recipes", o.toString()).putLong("synced", System.currentTimeMillis()).apply()
        return added
    }

    /** Von der KI erkanntes Rezept speichern. */
    fun addFromJson(o: JSONObject): String {
        fun list(a: JSONArray?) = a?.let { (0 until it.length()).map { i -> it.optString(i) } }.orEmpty().filter { it.isNotBlank() }
        val r = YazioClient.Recipe(
            name = o.optString("name").ifBlank { "Rezept" },
            kcal = o.optInt("kcal_pro_portion").takeIf { it > 0 }, proteinG = o.optInt("protein_pro_portion").takeIf { it > 0 },
            servings = o.optInt("portionen", 1), ingredients = list(o.optJSONArray("zutaten")), steps = list(o.optJSONArray("schritte")),
        )
        merge(listOf(r)); return r.name
    }

    fun remove(name: String) {
        val o = JSONObject(prefs.getString("recipes", "{}")!!); o.remove(name.trim().lowercase())
        prefs.edit().putString("recipes", o.toString()).apply()
    }

    fun lastSync(): Long = prefs.getLong("synced", 0)

    /** Rezept im selben Format wie eine Plan-Mahlzeit, damit die Rezeptansicht es anzeigen kann. */
    fun asMeal(r: YazioClient.Recipe): JSONObject = JSONObject()
        .put("name", "Aus deinem Rezeptbuch").put("rezept", r.name).put("uhrzeit", "")
        .put("kcal", r.kcal ?: 0).put("protein_g", r.proteinG ?: 0).put("portionen", r.servings ?: 1)
        .put("zutaten", JSONArray(r.ingredients)).put("schritte", JSONArray(r.steps))

    private fun toJson(r: YazioClient.Recipe) = JSONObject()
        .put("name", r.name).put("kcal", r.kcal ?: JSONObject.NULL).put("protein", r.proteinG ?: JSONObject.NULL)
        .put("servings", r.servings ?: 1).put("ingredients", JSONArray(r.ingredients)).put("steps", JSONArray(r.steps))

    private fun fromJson(o: JSONObject): YazioClient.Recipe {
        fun list(a: JSONArray?) = a?.let { (0 until it.length()).map { i -> it.getString(i) } }.orEmpty()
        return YazioClient.Recipe(
            name = o.optString("name"),
            kcal = if (o.isNull("kcal")) null else o.optInt("kcal"),
            proteinG = if (o.isNull("protein")) null else o.optInt("protein"),
            servings = o.optInt("servings", 1),
            ingredients = list(o.optJSONArray("ingredients")),
            steps = list(o.optJSONArray("steps")),
        )
    }
}
