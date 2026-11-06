package cc.maue.essensplan.data

import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * YAZIO hat keine offizielle API. Das hier nutzt dieselbe Schnittstelle wie die YAZIO-App
 * (Login mit E-Mail/Passwort). Kann sich ohne Vorwarnung ändern; dann liefert
 * recipes() eine leere Liste und der Plan wird ohne deine Rezepte erstellt.
 */
class YazioClient(private val email: String, private val password: String) {

    private val base = "https://yzapi.yazio.com/v15"
    // Öffentliche Client-Kennung der YAZIO-App
    private val clientId = "1_4hiybetvfksgw40o0sog4s884kwc840wwso8go4k8c04goo4c"
    private val clientSecret = "6rok2m65xuskgkgogw40wkkk8sw0osg84s8cggsc4woos4s8o4"

    data class Recipe(val name: String, val kcal: Int?, val proteinG: Int?, val servings: Int?, val ingredients: List<String>)

    private fun token(): String {
        val body = FormBody.Builder()
            .add("client_id", clientId).add("client_secret", clientSecret)
            .add("username", email).add("password", password)
            .add("grant_type", "password").build()
        Http.client.newCall(Request.Builder().url("$base/oauth/token").post(body).build()).execute().use {
            val txt = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw HttpException(it.code, txt)
            return JSONObject(txt).getString("access_token")
        }
    }

    private fun get(path: String, token: String): String =
        Http.client.newCall(Request.Builder().url("$base$path").header("Authorization", "Bearer $token").build())
            .execute().use {
                val txt = it.body?.string().orEmpty()
                if (!it.isSuccessful) throw HttpException(it.code, txt)
                txt
            }

    /** Verbindungstest: Login versuchen. */
    fun test(): String { token(); return "Verbunden" }

    /** Eigene und gespeicherte Rezepte. */
    fun recipes(): List<Recipe> {
        if (email.isBlank()) return emptyList()
        val t = token()
        val ids = mutableSetOf<String>()
        for (path in listOf("/user/recipes", "/user/favorites/recipes")) {
            runCatching { collectIds(JSONArray(get(path, t)), ids) }
        }
        return ids.take(60).mapNotNull { id ->
            runCatching { parseRecipe(JSONObject(get("/recipes/$id", t))) }.getOrNull()
        }
    }

    private fun collectIds(arr: JSONArray, into: MutableSet<String>) {
        for (i in 0 until arr.length()) {
            when (val v = arr.get(i)) {
                is String -> into += v
                is JSONObject -> (v.optString("recipe_id").ifBlank { v.optString("id") }).takeIf { it.isNotBlank() }?.let { into += it }
            }
        }
    }

    private fun parseRecipe(o: JSONObject): Recipe {
        val nutr = o.optJSONObject("nutrients")
        val servings = o.optInt("portion_count", 1).coerceAtLeast(1)
        val ing = o.optJSONArray("servings") ?: o.optJSONArray("ingredients") ?: JSONArray()
        val list = (0 until ing.length()).map { i ->
            val x = ing.getJSONObject(i)
            listOfNotNull(
                x.optString("amount").takeIf { it.isNotBlank() && it != "null" },
                x.optString("serving").takeIf { it.isNotBlank() && it != "null" },
                x.optString("name").ifBlank { x.optString("product_name") },
            ).joinToString(" ")
        }
        return Recipe(
            name = o.optString("name"),
            kcal = nutr?.optDouble("energy.energy")?.takeIf { !it.isNaN() }?.div(servings)?.toInt(),
            proteinG = nutr?.optDouble("nutrient.protein")?.takeIf { !it.isNaN() }?.div(servings)?.toInt(),
            servings = servings,
            ingredients = list,
        )
    }
}
