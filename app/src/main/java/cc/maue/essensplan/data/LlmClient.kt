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

    private fun geminiRaw(body: JSONObject, retried: Boolean = false): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${settings.geminiModel}:generateContent"
        val req = Request.Builder().url(url).header("x-goog-api-key", settings.geminiKey)
            .post(body.toString().toRequestBody(json)).build()
        Http.client.newCall(req).execute().use {
            val txt = it.body?.string().orEmpty()
            if (it.code == 404 && !retried) {
                // Modell abgeschaltet: empfohlenes Modell aus der Fehlermeldung oder neuestes Flash-Modell nehmen
                val suggested = Regex("models/(gemini[\\w.\\-]+)").findAll(txt).map { m -> m.groupValues[1] }
                    .firstOrNull { m -> m != settings.geminiModel } ?: newestFlash()
                if (suggested != null) { settings.geminiModel = suggested; return geminiRaw(body, retried = true) }
            }
            if (!it.isSuccessful) throw HttpException(it.code, txt)
            val parts = JSONObject(txt).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            return (0 until parts.length()).joinToString("") { i -> parts.getJSONObject(i).optString("text") }
        }
    }

    /** Neuestes verfügbares Flash-Modell, das Text erzeugen kann. */
    private fun newestFlash(): String? = runCatching {
        val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200")
            .header("x-goog-api-key", settings.geminiKey).build()
        val arr = Http.client.newCall(req).execute().use { JSONObject(it.body!!.string()).getJSONArray("models") }
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { m -> m.optJSONArray("supportedGenerationMethods").toString().contains("generateContent") }
            .map { it.getString("name").removePrefix("models/") }
            .filter { it.contains("flash") && !it.contains("lite") && !it.contains("image") && !it.contains("tts") && !it.contains("preview") }
            .maxByOrNull { n -> Regex("(\\d+(\\.\\d+)?)").find(n)?.value?.toDoubleOrNull() ?: 0.0 }
    }.getOrNull()
}
