package cc.maue.essensplan.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Erst Claude. Wenn Claude nicht kann (Guthaben leer, Limit erreicht, überlastet, kein Key),
 * automatisch Gemini.
 */
class LlmClient(private val settings: Settings) {

    data class Result(val text: String, val provider: String)

    private val json = "application/json".toMediaType()

    fun complete(system: String, user: String): Result {
        var claudeError: Exception? = null
        if (settings.claudeKey.isNotBlank()) {
            try {
                return Result(claude(system, user), "Claude")
            } catch (e: HttpException) {
                // 400 mit "credit balance", 401/403 Key-Problem, 429 Limit, 5xx/529 überlastet
                claudeError = e
            } catch (e: Exception) {
                claudeError = e
            }
        }
        if (settings.geminiKey.isBlank()) throw claudeError ?: IllegalStateException("Kein API-Key hinterlegt")
        return Result(gemini(system, user), if (claudeError != null) "Gemini (Claude nicht verfügbar)" else "Gemini")
    }

    private fun claude(system: String, user: String): String {
        val body = JSONObject()
            .put("model", settings.claudeModel)
            .put("max_tokens", 8000)
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

    private fun gemini(system: String, user: String): String {
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", user)))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${settings.geminiModel}:generateContent"
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
}
