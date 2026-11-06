package cc.maue.essensplan.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

/**
 * Aktuelle Prospektangebote der Supermärkte rund um deine PLZ (Quelle: marktguru, inoffiziell).
 * Für jede Zeile der Einkaufsliste werden Angebote gesucht und je Markt summiert.
 * Ergebnis: Empfehlung, in welchem Markt (oder welcher Kombi aus zwei Märkten) du am meisten sparst.
 */
class Offers(private val zip: String, private val stores: List<String>) {

    data class Offer(val item: String, val store: String, val product: String, val price: Double, val oldPrice: Double?, val until: String)
    data class Recommendation(val text: String, val offers: List<Offer>)

    private fun keys(): Pair<String, String> {
        val html = Http.client.newCall(Request.Builder().url("https://www.marktguru.de/").header("User-Agent", "Mozilla/5.0").build())
            .execute().use { it.body?.string().orEmpty() }
        val api = Regex("\"apiKey\"\\s*:\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1)
        val client = Regex("\"clientKey\"\\s*:\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1)
        require(api != null && client != null) { "marktguru-Schlüssel nicht gefunden" }
        return api to client
    }

    private fun search(q: String, k: Pair<String, String>): List<Offer> {
        val url = "https://api.marktguru.de/api/v1/offers/search".toHttpUrl().newBuilder()
            .addQueryParameter("as", "web").addQueryParameter("limit", "25").addQueryParameter("offset", "0")
            .addQueryParameter("q", q).addQueryParameter("zipCode", zip).build()
        val req = Request.Builder().url(url).header("x-apikey", k.first).header("x-clientkey", k.second).build()
        val txt = Http.client.newCall(req).execute().use { if (!it.isSuccessful) return emptyList(); it.body?.string().orEmpty() }
        val res = JSONObject(txt).optJSONArray("results") ?: return emptyList()
        return (0 until res.length()).mapNotNull { i ->
            val o = res.getJSONObject(i)
            val store = o.optJSONArray("advertisers")?.optJSONObject(0)?.optString("name") ?: return@mapNotNull null
            if (stores.isNotEmpty() && stores.none { store.contains(it.trim(), ignoreCase = true) }) return@mapNotNull null
            val price = o.optDouble("price").takeIf { !it.isNaN() } ?: return@mapNotNull null
            Offer(
                item = q, store = store,
                product = listOfNotNull(o.optJSONObject("brand")?.optString("name"), o.optJSONObject("product")?.optString("name")).joinToString(" "),
                price = price,
                oldPrice = o.optDouble("oldPrice").takeIf { !it.isNaN() && it > price },
                until = o.optJSONArray("validityDates")?.optJSONObject(0)?.optString("to")?.take(10).orEmpty(),
            )
        }
    }

    fun recommend(shopping: List<String>): Recommendation {
        if (zip.isBlank()) return Recommendation("Keine PLZ hinterlegt, daher kein Angebotsvergleich.", emptyList())
        val k = keys()
        // Pro Artikel und Markt das günstigste Angebot
        val best = shopping.flatMap { item ->
            search(item, k).groupBy { it.store }.map { (_, l) -> l.minBy { it.price } }
        }
        if (best.isEmpty()) return Recommendation("Diese Woche keine passenden Angebote in $zip gefunden. Nimm den Markt, der am nächsten liegt.", emptyList())

        fun saving(o: Offer) = (o.oldPrice ?: o.price) - o.price + 0.01 // jeder Treffer zählt etwas
        val perStore = best.groupBy { it.store }.mapValues { (_, l) -> l.sumOf { saving(it) } to l }
        val ranked = perStore.entries.sortedByDescending { it.value.first }
        val top = ranked.first()

        val text = buildString {
            appendLine("Empfehlung: ${top.key} (${top.value.second.size} Artikel im Angebot, ca. %.2f € gespart)".format(top.value.first))
            top.value.second.forEach { appendLine("  • ${it.item}: ${it.product} %.2f €".format(it.price) + (it.oldPrice?.let { o -> " statt %.2f €".format(o) } ?: "") + (if (it.until.isNotBlank()) " bis ${it.until}" else "")) }
            ranked.drop(1).take(2).forEach { e ->
                val extra = e.value.second.filter { o -> top.value.second.none { it.item == o.item } }
                if (extra.isNotEmpty()) appendLine("Lohnt sich zusätzlich, wenn du vorbeikommst: ${e.key} für " + extra.joinToString { it.item })
            }
        }
        return Recommendation(text, best)
    }
}
