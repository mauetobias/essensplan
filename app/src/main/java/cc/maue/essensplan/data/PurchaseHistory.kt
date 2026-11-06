package cc.maue.essensplan.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Lernt aus REWE-eBons, was du typischerweise kaufst.
 * Jeder Bon wird einmal von der KI gelesen (PDF → Artikelliste), danach bleibt alles lokal.
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

        val prompt = """
            Das ist ein Kassenbon. Lies alle gekauften Artikel aus und vereinfache die Bon-Kürzel zu normalen Produktnamen
            (z.B. "JA! H-MILCH 1,5%" → "H-Milch 1,5 %"). Pfand, Tüten und Rabattzeilen weglassen.
            Antworte nur mit JSON: {"datum":"YYYY-MM-DD","markt":"","artikel":[{"name":"","kategorie":"Obst/Gemüse|Kühlregal|Fleisch/Wurst|Brot/Backwaren|Vorrat|Snacks|Getränke|Drogerie|Tiefkühl|Sonstiges","preis":0.0}]}
        """.trimIndent()
        val res = LlmClient(settings).readPdf(Base64.encodeToString(bytes, Base64.NO_WRAP), prompt)
        val o = JSONObject("{" + res.substringAfter("{").substringBeforeLast("}") + "}")
        val date = o.optString("datum").ifBlank { LocalDate.now().toString() }
        val arr = o.optJSONArray("artikel") ?: JSONArray()

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
            Pantry(context).addPurchased((0 until arr.length()).map { Triple(arr.getJSONObject(it).optString("name"), 1.0, "Stück") })
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
