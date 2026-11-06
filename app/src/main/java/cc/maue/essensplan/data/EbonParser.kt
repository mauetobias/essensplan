package cc.maue.essensplan.data

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream

/**
 * Liest REWE-eBons direkt im Handy aus, ohne KI und ohne Internet.
 * Ein eBon ist ein Text-PDF; jede Artikelzeile sieht so aus: "SKYR NATUR   1,29 B",
 * Mengen stehen in der Zeile darunter: "2 Stk x 0,95".
 */
object EbonParser {

    data class Item(val name: String, val qty: Int, val price: Double, val category: String)
    data class Receipt(val date: String, val store: String, val total: Double, val items: List<Item>)

    private val itemRx = Regex("""^\s*(.+?)\s+(-?\d+,\d{2})\s+[AB]\b\s*\*?\s*$""")
    private val qtyRx = Regex("""^\s*(\d+)\s*Stk\s*x\s*(\d+,\d{2})""")
    private val dateRx = Regex("""(\d{2})\.(\d{2})\.(\d{4})\s+\d{2}:\d{2}""")
    private val sumRx = Regex("""SUMME\s+EUR\s+(\d+,\d{2})""")
    private val skipRx = Regex("PFAND|LEERG|SUMME|RABATT|COUPON|TUETE|TÜTE|TRAGETASCHE|BONUS", RegexOption.IGNORE_CASE)

    fun text(context: Context, bytes: ByteArray): String {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(ByteArrayInputStream(bytes)).use { doc ->
            return PDFTextStripper().apply { sortByPosition = true }.getText(doc)
        }
    }

    fun parse(text: String): Receipt {
        val items = mutableListOf<Item>()
        var lastWasItem = false
        var date = ""; var total = 0.0
        val store = when {
            text.contains("REWE", true) -> "REWE"
            text.contains("EDEKA", true) -> "Edeka"
            text.contains("LIDL", true) -> "Lidl"
            text.contains("ALDI", true) -> "Aldi"
            else -> "Supermarkt"
        }
        for (line in text.lines()) {
            if (date.isEmpty()) dateRx.find(line)?.let { date = "${it.groupValues[3]}-${it.groupValues[2]}-${it.groupValues[1]}" }
            sumRx.find(line)?.let { total = it.groupValues[1].replace(",", ".").toDouble() }
            val q = qtyRx.find(line)
            if (q != null) {
                // Menge gehört nur zum direkt davor stehenden Artikel, nicht zu Pfand
                if (lastWasItem) items[items.lastIndex] = items.last().copy(qty = q.groupValues[1].toInt())
                lastWasItem = false; continue
            }
            val m = itemRx.find(line)
            if (m == null) { if (line.isNotBlank()) lastWasItem = false; continue }
            val name = m.groupValues[1].trim()
            val price = m.groupValues[2].replace(",", ".").toDouble()
            if (skipRx.containsMatchIn(name) || price <= 0) { lastWasItem = false; continue }
            items += Item(name, 1, price, category(name))
            lastWasItem = true
        }
        return Receipt(date, store, total, items)
    }

    /** Grobe Einordnung anhand typischer Bon-Kürzel. */
    fun category(n: String): String {
        val t = n.lowercase()
        fun has(vararg w: String) = w.any { t.contains(it) }
        return when {
            has("zero", "cola", "spezi", "volvic", "wasser", "saft", "tee ", "eistee", "monster", "red bull", "energy", "rockstar", "pepper", "0,33ds", "0,5l", "1,5l", "limo") -> "Getränke"
            has("lenor", "ariel", "persil", "reiniger", "spuel", "spül", "backpapier", "folie", "zahn", "duschgel", "shampoo", "deo", "kuechenr", "toilett") -> "Drogerie/Haushalt"
            has("apfel", "banane", "zwiebel", "knoblauch", "tomate", "gurke", "paprika", "salat", "melone", "beere", "zitrone", "kartoffel", "moehre", "möhre", "brokkoli", "lauch", "pilz", "champ", "avocado", "obst") -> "Obst/Gemüse"
            has("milch", "skyr", "quark", "joghurt", "kaese", "käse", "butter", "sahne", "harzer", "mozz", "feta", "eier", "frischk", "pudd") -> "Kühlregal"
            has("haehn", "hähn", "huhn", "rind", "hack", "schwein", "salami", "schinken", "wurst", "pute", "lachs", "aufschnitt", "rohsch", "salakis") -> "Fleisch/Wurst"
            has("brot", "broet", "toast", "brötchen", "wrap", "tortilla", "baguette") -> "Brot/Backwaren"
            has("riegel", "chips", "popcorn", "waffel", "giotto", "schoko", "keks", "gummi", "nuss", "protein", "salzstang", "cracker") -> "Snacks"
            has("tk ", "tiefk", "pizza", "eis ") -> "Tiefkühl"
            has("mehl", "zucker", "reis", "nudel", "pasta", "muesli", "müsli", "hafer", "oel", "öl", "essig", "gewuerz", "salz", "pfeffer", "dose", "bohnen", "linsen") -> "Vorrat"
            else -> "Sonstiges"
        }
    }
}
