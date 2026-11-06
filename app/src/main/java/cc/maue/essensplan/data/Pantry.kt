package cc.maue.essensplan.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Das "Gehirn" für zu Hause:
 *  * Grundvorrat (Zucker, Mehl, Salz, Öl, Gewürze …) gilt als vorhanden, bis du ihn als leer markierst.
 *  * Eingekaufte Artikel kommen mit Menge in den Vorrat ("Einkauf erledigt").
 *  * Geplanter Verbrauch wird abgezogen, sobald der Tag vorbei ist.
 *  * "Immer da"-Artikel (Wasser, Tee, Energy) werden nachgekauft, wenn sie unter den Mindestbestand fallen.
 * Nachgekauft wird erst, wenn der Bestand den Bedarf plus Puffer nicht mehr deckt.
 */
class Pantry(context: Context) {

    private val prefs = context.getSharedPreferences("pantry", Context.MODE_PRIVATE)

    data class Item(
        val name: String,
        var qty: Double,          // in unit
        var unit: String,         // g, ml, Stück, Flasche, Dose, Beutel
        var staple: Boolean = false,  // Grundvorrat: gilt als da, solange nicht leer markiert
        var empty: Boolean = false,
        var minQty: Double = 0.0, // >0 = "Immer da"-Artikel mit Mindestbestand
        var lastBought: String = "",
    )

    /** Geplanter Verbrauch eines Tages, wird nach Ablauf des Tages abgezogen. */
    data class Usage(val day: LocalDate, val name: String, val qty: Double, val unit: String)

    companion object {
        val DEFAULT_STAPLES = listOf(
            "Salz", "Pfeffer", "Zucker", "Mehl", "Olivenöl", "Rapsöl", "Essig", "Senf", "Sojasauce",
            "Paprikapulver", "Oregano", "Basilikum getrocknet", "Chiliflocken", "Knoblauchpulver",
            "Currypulver", "Zimt", "Backpulver", "Gemüsebrühe", "Tomatenmark",
        )
        val DEFAULT_ALWAYS = listOf(
            Item("Volvic 1,5 L (Sixpack)", 0.0, "Sixpack", minQty = 1.0),
            Item("Grüner Tee", 0.0, "Packung", minQty = 1.0),
            Item("Schwarzer Tee", 0.0, "Packung", minQty = 1.0),
            Item("Energy Drink Zero", 0.0, "Dose", minQty = 4.0),
            Item("Zero-Getränk (Cola Zero o.ä.)", 0.0, "Flasche", minQty = 2.0),
            Item("High-Protein-Salamisticks", 0.0, "Packung", minQty = 1.0),
            Item("Proteinriegel", 0.0, "Stück", minQty = 4.0),
            Item("Maiswaffeln gesalzen", 0.0, "Packung", minQty = 1.0),
            Item("Popcorn süß oder Karamell", 0.0, "Packung", minQty = 1.0),
        )
        private const val BUFFER = 1.2 // 20 % Puffer

        fun norm(s: String) = s.lowercase().replace(Regex("[^a-zäöüß0-9 ]"), "").trim()
    }

    fun items(): MutableList<Item> {
        val raw = prefs.getString("items", null)
        if (raw == null) {
            val init = (DEFAULT_STAPLES.map { Item(it, 1.0, "Vorrat", staple = true) } + DEFAULT_ALWAYS).toMutableList()
            save(init); return init
        }
        val a = JSONArray(raw)
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Item(o.getString("name"), o.optDouble("qty", 0.0), o.optString("unit"), o.optBoolean("staple"),
                o.optBoolean("empty"), o.optDouble("min", 0.0), o.optString("bought"))
        }.toMutableList()
    }

    fun save(list: List<Item>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("name", it.name).put("qty", it.qty).put("unit", it.unit).put("staple", it.staple)
                .put("empty", it.empty).put("min", it.minQty).put("bought", it.lastBought))
        }
        prefs.edit().putString("items", a.toString()).apply()
    }

    fun find(list: List<Item>, name: String) = list.firstOrNull { norm(it.name) == norm(name) }
        ?: list.firstOrNull { norm(name).contains(norm(it.name)) || norm(it.name).contains(norm(name)) }

    /** Gekaufte Artikel in den Vorrat buchen. */
    fun addPurchased(bought: List<Triple<String, Double, String>>) {
        val list = items()
        val today = LocalDate.now().toString()
        for ((name, qty, unit) in bought) {
            val it = find(list, name)
            if (it == null) list += Item(name, qty, unit, lastBought = today)
            else { if (it.unit == unit || it.qty == 0.0) { it.qty += qty; it.unit = unit } else it.qty = qty; it.empty = false; it.lastBought = today }
        }
        save(list)
    }

    // ---------- Verbrauch ----------

    fun planUsage(usage: List<Usage>) {
        val keep = pendingUsage().filter { u -> usage.none { it.day == u.day } } // neuer Plan ersetzt alte Tage
        val a = JSONArray()
        (keep + usage).forEach { a.put(JSONObject().put("d", it.day.toString()).put("n", it.name).put("q", it.qty).put("u", it.unit)) }
        prefs.edit().putString("usage", a.toString()).apply()
    }

    private fun pendingUsage(): List<Usage> {
        val a = JSONArray(prefs.getString("usage", "[]"))
        return (0 until a.length()).map { a.getJSONObject(it) }
            .map { Usage(LocalDate.parse(it.getString("d")), it.getString("n"), it.getDouble("q"), it.getString("u")) }
    }

    /** Vergangene Tage abbuchen. Wird bei jedem Planlauf aufgerufen. */
    fun consumePastDays() {
        val (past, future) = pendingUsage().partition { it.day.isBefore(LocalDate.now()) }
        if (past.isEmpty()) return
        val list = items()
        past.forEach { u -> find(list, u.name)?.takeIf { !it.staple }?.let { it.qty = (it.qty - u.qty).coerceAtLeast(0.0) } }
        list.removeAll { !it.staple && it.minQty == 0.0 && it.qty <= 0.0 }
        save(list)
        val a = JSONArray(); future.forEach { a.put(JSONObject().put("d", it.day.toString()).put("n", it.name).put("q", it.qty).put("u", it.unit)) }
        prefs.edit().putString("usage", a.toString()).apply()
    }

    /** Text für die KI: was ist da. */
    fun describe(): String {
        val list = items()
        val staples = list.filter { it.staple && !it.empty }.joinToString(", ") { it.name }
        val stock = list.filter { !it.staple && it.qty > 0 }.joinToString(", ") { "${it.name} ${fmt(it.qty)} ${it.unit}" }
        val emptyStaples = list.filter { it.staple && it.empty }.joinToString(", ") { it.name }
        return buildString {
            appendLine("Grundvorrat vorhanden (nicht kaufen): $staples")
            appendLine("Weitere Vorräte: ${stock.ifBlank { "keine" }}")
            if (emptyStaples.isNotBlank()) appendLine("Grundvorrat LEER, mit auf die Liste: $emptyStaples")
        }
    }

    /**
     * Einkaufsbedarf aus geplantem Verbrauch minus Vorrat (mit Puffer),
     * plus leerer Grundvorrat, plus "Immer da" unter Mindestbestand.
     */
    fun shoppingNeeds(needed: List<Triple<String, Double, String>>): List<Pair<String, String>> {
        val list = items()
        val out = linkedMapOf<String, String>()
        for ((name, qty, unit) in needed) {
            val have = find(list, name)
            if (have?.staple == true && !have.empty) continue
            val missing = qty * BUFFER - (have?.takeIf { it.unit == unit }?.qty ?: 0.0)
            if (missing > 0.05 * qty) out[name] = "${fmt(missing)} $unit"
        }
        list.filter { it.staple && it.empty }.forEach { out[it.name] = "" }
        list.filter { it.minQty > 0 && it.qty < it.minQty }.forEach { out[it.name] = "${fmt(it.minQty - it.qty)} ${it.unit}" }
        return out.map { it.key to it.value }
    }

    private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else "%.1f".format(d)
}
