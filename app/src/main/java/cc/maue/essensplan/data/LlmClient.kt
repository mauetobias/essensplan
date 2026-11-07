package cc.maue.essensplan.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gemini zuerst (kostenlos mit Google-Konto). Claude nur, wenn ein Schlüssel hinterlegt ist,
 * als Reserve. Veraltete Gemini-Modelle werden automatisch durch das empfohlene ersetzt.
 */
class LlmClient(private val settings: Settings) {

    data class Result(val text: String, val provider: String)

    private val json = "application/json".toMediaType()

    fun complete(system: String, user: String): Result {
        var firstError: Exception? = null
        if (settings.geminiKey.isNotBlank()) {
            try { return Result(gemini(system, user), "Gemini") } catch (e: Exception) { firstError = e }
        }
        if (settings.claudeKey.isNotBlank()) {
            try { return Result(claude(system, user), "Claude") } catch (e: Exception) { if (firstError == null) firstError = e }
        }
        throw firstError ?: IllegalStateException("Kein KI-Schlüssel hinterlegt. Bitte in den Einstellungen bei Gemini eintragen.")
    }

    /** Rezept aus Screenshot, Foto oder kopiertem Text erkennen (für das Rezeptbuch). */
    fun extractRecipe(text: String?, imageBase64: String?, mime: String = "image/jpeg"): JSONObject {
        val parts = JSONArray()
        if (imageBase64 != null) parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", mime).put("data", imageBase64)))
        parts.put(JSONObject().put("text", """
            Lies das Rezept aus ${if (imageBase64 != null) "dem Bild" else "diesem Text"} und antworte nur mit JSON:
            {"name":"","portionen":1,"kcal_pro_portion":0,"protein_pro_portion":0,"zutaten":["200 g Hähnchenbrust", "…"],"schritte":["…"]}
            Fehlen Nährwerte, schätze sie realistisch. Deutsch, keine Gedankenstriche.
            ${text.orEmpty()}
        """.trimIndent()))
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        val res = geminiRaw(body)
        return JSONObject("{" + res.substringAfter("{").substringBeforeLast("}") + "}")
    }

    /** Mehrere Screenshots eines Rezepts zusammen lesen. Liefert null, wenn kein Rezept zu sehen ist. */
    fun extractRecipeFromImages(images: List<Pair<String, String>>): JSONObject? {
        val parts = JSONArray()
        images.forEach { (b64, mime) -> parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", mime).put("data", b64))) }
        parts.put(JSONObject().put("text", """
            Diese Screenshots stammen vermutlich aus der App YAZIO und zeigen zusammen EIN Rezept (Zutaten, Nährwerte, Zubereitung).
            Ist auf keinem Bild ein Rezept zu sehen, antworte nur mit {"kein_rezept":true}.
            Sonst antworte nur mit JSON:
            {"name":"","portionen":1,"kcal_pro_portion":0,"protein_pro_portion":0,"zutaten":["200 g Hähnchenbrust", "…"],"schritte":["…"]}
            Fehlen Nährwerte oder Schritte, ergänze sie realistisch. Deutsch, keine Gedankenstriche.
        """.trimIndent()))
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        val o = JSONObject("{" + geminiRaw(body).substringAfter("{").substringBeforeLast("}") + "}")
        return o.takeIf { !it.optBoolean("kein_rezept") && it.optString("name").isNotBlank() }
    }

    /** Kurzer Verbindungstest für den Verbinden-Knopf. */
    fun testGemini(): String { geminiRaw(textBody("Antworte nur mit {\"ok\":true}")); return "Verbunden (${settings.geminiModel})" }
    fun testClaude(): String { claude("Antworte nur mit OK.", "Test"); return "Verbunden (${settings.claudeModel})" }

    private fun claude(system: String, user: String): String {
        val body = JSONObject()
            .put("model", settings.claudeModel)
            .put("max_tokens", 20000)
            .put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
        val req = Request.Builder().url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", settings.claudeKey)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(json)).build()
        Http.client.newCall(req).execute().use {
            val txt = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw HttpException(it.code, txt)
            val content = JSONObject(txt).getJSONArray("content")
            return (0 until content.length()).map { i -> content.getJSONObject(i) }
                .filter { it.optString("type") == "text" }.joinToString("") { it.getString("text") }
        }
    }

    private fun textBody(user: String, system: String? = null) = JSONObject().apply {
        if (system != null) put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))))
        put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
    }

    private fun gemini(system: String, user: String): String = geminiRaw(textBody(user, system))

    /**
     * Gemini aufrufen. Bei Überlastung (503/500/429) bis zu 3 Mal mit Pause neu versuchen,
     * danach auf ein anderes Flash-Modell ausweichen. Abgeschaltete Modelle (404) werden ersetzt.
     */
    private fun geminiRaw(body: JSONObject): String {
        var lastError: Exception? = null
        val tried = mutableSetOf<String>()
        repeat(3) { round ->
            val model = settings.geminiModel
            tried += model
            for (attempt in 0 until 3) {
                try { return geminiCall(model, body) } catch (e: HttpException) {
                    lastError = e
                    when (e.code) {
                        404 -> {
                            val msg = e.message.orEmpty()
                            val next = Regex("models/(gemini[\\w.\\-]+)").findAll(msg).map { it.groupValues[1] }.firstOrNull { it !in tried }
                                ?: alternativeModels().firstOrNull { it !in tried }
                            if (next != null) { settings.geminiModel = next; return@repeat } else throw e
                        }
                        429, 500, 502, 503, 504 -> Thread.sleep(longArrayOf(3000, 8000, 15000)[attempt])
                        else -> throw e
                    }
                }
            }
            // Dauerhaft überlastet: anderes Modell für diesen Aufruf probieren
            val alt = alternativeModels().firstOrNull { it !in tried } ?: return@repeat
            try { return geminiCall(alt, body) } catch (e: Exception) { lastError = e; tried += alt }
        }
        throw lastError ?: IllegalStateException("Gemini nicht erreichbar")
    }

    private fun geminiCall(model: String, body: JSONObject): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val req = Request.Builder().url(url).header("x-goog-api-key", settings.geminiKey)
            .post(body.toString().toRequestBody(json)).build()
        Http.client.newCall(req).execute().use {
            val txt = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw HttpException(it.code, txt)
            val parts = JSONObject(txt).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            return (0 until parts.length()).joinToString("") { i -> parts.getJSONObject(i).optString("text") }
        }
    }

    /** Andere nutzbare Modelle, beste zuerst (Flash vor Flash-Lite, neueste Version zuerst). */
    private fun alternativeModels(): List<String> = runCatching {
        val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200")
            .header("x-goog-api-key", settings.geminiKey).build()
        val arr = Http.client.newCall(req).execute().use { JSONObject(it.body!!.string()).getJSONArray("models") }
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { m -> m.optJSONArray("supportedGenerationMethods").toString().contains("generateContent") }
            .map { it.getString("name").removePrefix("models/") }
            .filter { it.contains("flash") && !it.contains("image") && !it.contains("tts") && !it.contains("audio") && !it.contains("live") }
            .sortedWith(compareBy<String>({ it.contains("lite") }, { it.contains("preview") || it.contains("exp") })
                .thenByDescending { n -> Regex("(\\d+(\\.\\d+)?)").find(n)?.value?.toDoubleOrNull() ?: 0.0 })
    }.getOrDefault(emptyList())

}
