package cc.maue.essensplan.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lokales Rezeptbuch. Jedes Rezept, das einmal aus YAZIO geladen wurde, bleibt hier dauerhaft
 * gespeichert (Zutaten, Nährwerte, Zubereitung). Auch nach einer YAZIO-Kündigung bleibt alles da.
 */
class RecipeBook(private val context: Context) {

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

    /** Rezept zu einem Gerichtnamen aus dem Plan finden (für Foto und Details). */
    fun find(dish: String): YazioClient.Recipe? {
        val d = dish.trim().lowercase()
        if (d.isBlank()) return null
        return all().firstOrNull { it.name.lowercase() == d } ?: all().firstOrNull { d.contains(it.name.lowercase()) || it.name.lowercase().contains(d) }
    }

    /** Rezeptpaket (ZIP mit recipes.json und img/) importieren. Fotos liegen danach im App-Speicher. */
    fun importPack(input: java.io.InputStream): Int {
        val dir = java.io.File(context.filesDir, "recipe_img").apply { mkdirs() }
        var json: String? = null
        java.util.zip.ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                val name = e.name.substringAfterLast('/')
                when {
                    e.name.endsWith("recipes.json") -> json = z.readBytes().toString(Charsets.UTF_8)
                    e.name.contains("img/") && name.isNotBlank() -> java.io.File(dir, name).outputStream().use { z.copyTo(it) }
                }
            }
        }
        val root = JSONObject(json ?: error("Keine recipes.json im Paket"))
        val arr = root.optJSONArray("recipes") ?: JSONArray()
        fun list(a: JSONArray?) = a?.let { (0 until it.length()).map { i -> it.optString(i) } }.orEmpty()
        val recipes = (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            YazioClient.Recipe(
                name = o.optString("name"), kcal = o.optInt("kcal").takeIf { it > 0 }, proteinG = o.optInt("protein").takeIf { it > 0 },
                servings = o.optInt("servings", 1), ingredients = list(o.optJSONArray("ingredients")), steps = list(o.optJSONArray("steps")),
                image = o.optString("image").takeIf { it.isNotBlank() }?.let { java.io.File(dir, it).absolutePath },
                minutes = o.optInt("minutes").takeIf { it > 0 }, description = o.optString("description"),
                carbsG = if (o.isNull("carbs")) null else o.optInt("carbs"), fatG = if (o.isNull("fat")) null else o.optInt("fat"),
            )
        }
        merge(recipes)
        return recipes.size
    }

    fun lastSync(): Long = prefs.getLong("synced", 0)

    /** Rezept im selben Format wie eine Plan-Mahlzeit, damit die Rezeptansicht es anzeigen kann. */
    fun asMeal(r: YazioClient.Recipe): JSONObject = JSONObject()
        .put("name", "Aus deinem Rezeptbuch").put("rezept", r.name).put("uhrzeit", "")
        .put("kcal", r.kcal ?: 0).put("protein_g", r.proteinG ?: 0).put("portionen", r.servings ?: 1)
        .put("zutaten", JSONArray(r.ingredients)).put("schritte", JSONArray(r.steps))
        .put("kh_g", r.carbsG ?: 0).put("fett_g", r.fatG ?: 0).put("dauer_min", r.minutes ?: 0)
        .put("tipp", r.description).put("bild", r.image ?: "")

    private fun toJson(r: YazioClient.Recipe) = JSONObject()
        .put("name", r.name).put("kcal", r.kcal ?: JSONObject.NULL).put("protein", r.proteinG ?: JSONObject.NULL)
        .put("servings", r.servings ?: 1).put("ingredients", JSONArray(r.ingredients)).put("steps", JSONArray(r.steps))
        .put("image", r.image ?: JSONObject.NULL).put("minutes", r.minutes ?: JSONObject.NULL).put("desc", r.description)
        .put("carbs", r.carbsG ?: JSONObject.NULL).put("fat", r.fatG ?: JSONObject.NULL)

    private fun fromJson(o: JSONObject): YazioClient.Recipe {
        fun list(a: JSONArray?) = a?.let { (0 until it.length()).map { i -> it.getString(i) } }.orEmpty()
        return YazioClient.Recipe(
            name = o.optString("name"),
            kcal = if (o.isNull("kcal")) null else o.optInt("kcal"),
            proteinG = if (o.isNull("protein")) null else o.optInt("protein"),
            servings = o.optInt("servings", 1),
            ingredients = list(o.optJSONArray("ingredients")),
            steps = list(o.optJSONArray("steps")),
            image = o.optString("image").takeIf { it.isNotBlank() && it != "null" },
            minutes = if (o.isNull("minutes")) null else o.optInt("minutes"),
            description = o.optString("desc"),
            carbsG = if (o.isNull("carbs")) null else o.optInt("carbs"),
            fatG = if (o.isNull("fat")) null else o.optInt("fat"),
        )
    }
}
