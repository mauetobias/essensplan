package cc.maue.essensplan.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject

/** Ein Gericht zum Nachkochen: Zutaten zum Abhaken, Schritte zum Abhaken, Display bleibt an. */
class RecipeActivity : ComponentActivity() {

    companion object {
        fun open(context: Context, meal: JSONObject, dayLabel: String) =
            context.startActivity(Intent(context, RecipeActivity::class.java).putExtra("meal", meal.toString()).putExtra("day", dayLabel))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // beim Kochen nicht ausgehen
        val m = JSONObject(intent.getStringExtra("meal") ?: "{}")
        // Passendes Rezept aus dem Rezeptbuch: Foto und fehlende Details ergänzen
        val bookRecipe = cc.maue.essensplan.data.RecipeBook(this).find(m.optString("rezept"))
        if (bookRecipe != null) {
            if ((m.optJSONArray("zutaten")?.length() ?: 0) == 0) m.put("zutaten", JSONArray(bookRecipe.ingredients))
            if ((m.optJSONArray("schritte")?.length() ?: 0) == 0) m.put("schritte", JSONArray(bookRecipe.steps))
            if (m.optString("bild").isBlank()) m.put("bild", bookRecipe.image ?: "")
        }
        val photo = m.optString("bild").takeIf { it.isNotBlank() }?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() }
        val day = intent.getStringExtra("day").orEmpty()
        fun list(a: JSONArray?) = a?.let { (0 until it.length()).map { i -> it.optString(i) } }.orEmpty().filter { it.isNotBlank() }
        val ingredients = list(m.optJSONArray("zutaten"))
        val steps = list(m.optJSONArray("schritte"))

        setContent {
            FreshTheme {
                val gotIt = remember { mutableStateListOf<Int>() }
                val done = remember { mutableStateListOf<Int>() }
                Column(Modifier.fillMaxSize().background(Fresh.Bg).verticalScroll(rememberScrollState())) {
                    if (photo != null) androidx.compose.foundation.Image(
                        bitmap = photo, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                    )
                    HeroHeader(m.optString("rezept").ifBlank { m.optString("name") }, "$day · ${m.optString("uhrzeit")} · ${m.optString("name")}", mealEmoji(m.optString("name"), m.optString("rezept"))) {
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HeroStat("${m.optInt("kcal")}", "kcal")
                            HeroStat("${m.optInt("protein_g")} g", "Protein")
                            if (m.optInt("kh_g") > 0) HeroStat("${m.optInt("kh_g")} g", "Kohlenh.")
                            if (m.optInt("dauer_min") > 0) HeroStat("${m.optInt("dauer_min")}′", "Minuten")
                        }
                    }

                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (ingredients.isNotEmpty()) FreshCard(accent = Fresh.Citrus) {
                            Text("Zutaten" + (if (m.optInt("portionen") > 1) " für ${m.optInt("portionen")} Portionen" else ""), style = MaterialTheme.typography.headlineSmall)
                            Text("Antippen, was schon bereitliegt", color = Fresh.Soft, fontSize = 12.sp)
                            ingredients.forEachIndexed { i, z ->
                                val ok = i in gotIt
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                                        .clickable { if (ok) gotIt.remove(i) else gotIt.add(i) }.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.size(24.dp).clip(CircleShape).background(if (ok) Fresh.Leaf else Fresh.Peach),
                                        contentAlignment = Alignment.Center,
                                    ) { if (ok) Text("✓", color = Color.White, fontSize = 13.sp) }
                                    Text(z, Modifier.padding(start = 12.dp), color = if (ok) Fresh.Soft else Fresh.Ink,
                                        textDecoration = if (ok) TextDecoration.LineThrough else null)
                                }
                            }
                        }

                        if (steps.isNotEmpty()) FreshCard(accent = Fresh.Leaf) {
                            Text("So geht's", style = MaterialTheme.typography.headlineSmall)
                            steps.forEachIndexed { i, st ->
                                val ok = i in done
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(12.dp))
                                        .clickable { if (ok) done.remove(i) else done.add(i) },
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Box(
                                        Modifier.size(32.dp).clip(CircleShape).background(if (ok) Fresh.Leaf else Fresh.Mint),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(if (ok) "✓" else "${i + 1}", color = if (ok) Color.White else Fresh.Forest, fontWeight = FontWeight.Bold) }
                                    Text(st, Modifier.padding(start = 12.dp, top = 5.dp), style = MaterialTheme.typography.bodyMedium,
                                        color = if (ok) Fresh.Soft else Fresh.Ink)
                                }
                            }
                            if (steps.isNotEmpty() && done.size == steps.size) {
                                Spacer(Modifier.height(14.dp))
                                Text("Guten Appetit! 🌿", color = Fresh.Leaf, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }

                        if (m.optString("tipp").isNotBlank()) FreshCard(accent = Fresh.Lime) {
                            Text("💡  Tipp", style = MaterialTheme.typography.titleMedium)
                            Text(m.optString("tipp"), color = Fresh.Ink, modifier = Modifier.padding(top = 4.dp))
                        }

                        if (ingredients.isEmpty() && steps.isEmpty()) FreshCard {
                            Text("Für dieses Gericht gibt es noch kein Rezept. Beim nächsten Plan kommen zu jeder Mahlzeit Zutaten und Schritte mit.", color = Fresh.Soft)
                        }
                        Spacer(Modifier.height(30.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String) =
    Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .18f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text(label, color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
    }

/** Passendes Emoji zu Mahlzeit oder Gericht. */
fun mealEmoji(name: String, dish: String = ""): String {
    val t = (name + " " + dish).lowercase()
    return listOf(
        "riegel" to "🍫", "popcorn" to "🍿", "salami" to "🥓", "maiswaffel" to "🍘", "quark" to "🥣", "skyr" to "🥣",
        "joghurt" to "🥣", "müsli" to "🥣", "porridge" to "🥣", "rührei" to "🍳", "spiegelei" to "🍳", "eier" to "🍳", "omelett" to "🍳", "bowl" to "🥗",
        "salat" to "🥗", "pasta" to "🍝", "nudel" to "🍝", "reis" to "🍚", "curry" to "🍛", "hähnchen" to "🍗",
        "huhn" to "🍗", "rind" to "🥩", "steak" to "🥩", "burger" to "🍔", "wrap" to "🌯", "brot" to "🥪",
        "suppe" to "🍲", "kartoffel" to "🥔", "pizza" to "🍕", "shake" to "🥤", "obst" to "🍎", "banane" to "🍌",
        "snack" to "🥨", "frühstück" to "🥐", "abend" to "🍽️",
    ).firstOrNull { t.contains(it.first) }?.second ?: "🍽️"
}
