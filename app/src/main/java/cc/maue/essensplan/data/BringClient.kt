package cc.maue.essensplan.data

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Bring! hat ebenfalls keine offizielle API. Genutzt wird die Schnittstelle der Bring-App,
 * die auch Home Assistant verwendet. Artikel landen auf deiner Standardliste.
 */
class BringClient(private val email: String, private val password: String) {

    private val base = "https://api.getbring.com/rest/v2"
    private val apiKey = "cof4Nc6D8saplXjE3h3HXqHH8m7VU2i1Gs0g85Sp" // öffentlicher Schlüssel der Bring-App

    private data class Session(val userUuid: String, val token: String, val listUuid: String)

    private fun login(): Session {
        val body = FormBody.Builder().add("email", email).add("password", password).build()
        Http.client.newCall(Request.Builder().url("$base/bringauth").post(body).build()).execute().use {
            val txt = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw HttpException(it.code, txt)
            val o = JSONObject(txt)
            return Session(o.getString("uuid"), o.getString("access_token"), o.getString("bringListUUID"))
        }
    }

    /** items: Paare aus Artikelname und Menge, z.B. "Haferflocken" to "500 g". */
    fun addItems(items: List<Pair<String, String>>): Int {
        val s = login()
        var ok = 0
        for ((name, spec) in items) {
            val form = "uuid=${s.listUuid}&purchase=${enc(name)}&specification=${enc(spec)}"
            val req = Request.Builder()
                .url("$base/bringlists/${s.listUuid}")
                .put(form.toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaType()))
                .header("Authorization", "Bearer ${s.token}")
                .header("X-BRING-API-KEY", apiKey)
                .header("X-BRING-CLIENT", "android")
                .header("X-BRING-USER-UUID", s.userUuid)
                .header("X-BRING-COUNTRY", "DE")
                .build()
            Http.client.newCall(req).execute().use { if (it.isSuccessful) ok++ }
        }
        return ok
    }

    private fun enc(v: String) = java.net.URLEncoder.encode(v, "UTF-8")
}
