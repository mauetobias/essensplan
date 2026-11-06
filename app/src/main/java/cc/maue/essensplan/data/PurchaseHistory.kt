package cc.maue.essensplan.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Lernt aus REWE-eBons, was du typischerweise kaufst.
 * Jeder Bon wird direkt im Handy gelesen (Text-PDF → Artikelliste), alles bleibt lokal.
 */
class PurchaseHistory(private val context: Context) {

    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    data class Line(val name: String, val category: String, val price: Double)

    /** Name → (Anzahl Käufe, letztes Datum, Kategorie) */
    private fun load(): JSONObject = JSONObject(prefs.getString("items", "{}")!!)
    private fun seen(): MutableSet<String> = prefs.getStringSet("seen", emptySet())!!.toMutableSet()

    fun receiptCount() = seen().size

    /** Liest ein eBon-PDF, gibt Anzahl erkannter Artikel zurück (0 = schon importiert). */
    fun importPdf(bytes: ByteArray, settings: Settings): Int {
        val hash = bytes.contentHashCode().toString()
        val seen = seen()
        if (hash in seen) return 0

        // Direkt im Handy auslesen, ohne KI
        val receipt = EbonParser.parse(EbonParser.text(context, bytes))
        require(receipt.items.isNotEmpty()) { "Keine Artikel erkannt" }
        val date = receipt.date.ifBlank { LocalDate.now().toString() }
        val arr = JSONArray().apply {
            receipt.items.forEach { put(JSONObject().put("name", it.name).put("kategorie", it.category).put("preis", it.price).put("menge", it.qty)) }
        }

        val items = load()
        for (i in 0 until arr.length()) {
            val a = arr.getJSONObject(i)
            val key = a.optString("name").trim()
            if (key.isBlank()) continue
            val e = items.optJSONObject(key) ?: JSONObject().put("n", 0).put("last", "").put("cat", a.optString("kategorie"))
            e.put("n", e.optInt("n") + 1)
            if (date > e.optString("last")) e.put("last", date)
            e.put("price", a.optDouble("preis", 0.0))
            items.put(key, e)
        }
        seen += hash
        prefs.edit().putString("items", items.toString()).putStringSet("seen", seen).apply()

        // Frische Einkäufe der letzten 5 Tage direkt in den Vorrat
        if (LocalDate.parse(date).isAfter(LocalDate.now().minusDays(5))) {
            Pantry(context).addPurchased(receipt.items.filter { it.category != "Drogerie/Haushalt" }.map { Triple(it.name, it.qty.toDouble(), "Stück") })
        }
        return arr.length()
    }

    /** Häufigste Artikel, gruppiert nach Kategorie, für die KI. */
    fun summary(limit: Int = 50): String {
        val items = load()
        if (items.length() == 0) return "noch keine Daten"
        val list = items.keys().asSequence().map { k -> k to items.getJSONObject(k) }
            .sortedByDescending { it.second.optInt("n") }.take(limit).toList()
        return list.groupBy { it.second.optString("cat").ifBlank { "Sonstiges" } }
            .entries.joinToString("; ") { (cat, l) -> "$cat: " + l.joinToString(", ") { "${it.first} (${it.second.optInt("n")}x)" } }
    }

    fun top(limit: Int = 30): List<Pair<String, Int>> {
        val items = load()
        return items.keys().asSequence().map { it to items.getJSONObject(it).optInt("n") }
            .sortedByDescending { it.second }.take(limit).toList()
    }
}
