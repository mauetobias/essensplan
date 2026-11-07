package cc.maue.essensplan.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.maue.essensplan.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Daten-Tab: alles, was die App zieht und wie sie daraus rechnet. Zum Gegenprüfen. */
@Composable
fun DataTab() {
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf(runCatching { JSONObject(settings.lastInputs) }.getOrNull()) }
    var live by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var book by remember { mutableStateOf(RecipeBook(ctx).all()) }
    var bookMsg by remember { mutableStateOf("") }

    fun reload() {
        busy = true
        scope.launch {
            data = withContext(Dispatchers.IO) { liveInputs(ctx) }
            live = true; busy = false
        }
    }
    LaunchedEffect(Unit) { if (data == null) reload() }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Deine Daten", style = MaterialTheme.typography.headlineSmall)
                Text(
                    data?.let { (if (live) "Live gelesen " else "Stand des letzten Plans, ") + it.optString("time") } ?: "Noch keine Daten",
                    color = Fresh.Soft, fontSize = 12.sp,
                )
            }
            OutlinedButton(onClick = { reload() }, enabled = !busy, shape = RoundedCornerShape(14.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Fresh.Leaf) else Text("↻ Live")
            }
        }

        val d = data
        if (d != null) {
            Block("⚖️  Körper (Waage)", Fresh.Leaf) {
                Rows(d.optJSONArray("body"))
                ManualBody(settings) { reload() }
            }
            Block("⌚  Aktivität und Schlaf (Garmin)", Fresh.Citrus) {
                Rows(d.optJSONArray("activity"))
                Expandable("Tag für Tag", d.optJSONArray("daily"))
                Expandable("Letzte Trainings", d.optJSONArray("workouts"))
            }
            Block("📅  Kalender", Fresh.Lime) {
                val ev = d.optJSONArray("events") ?: JSONArray()
                if (ev.length() == 0) Hint("Keine Termine im Zeitraum gefunden. Ist der Kalender freigegeben?")
                for (i in 0 until ev.length()) ev.getJSONObject(i).let { e ->
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(e.optString("day"), color = Fresh.Soft, fontSize = 12.sp, modifier = Modifier.width(78.dp))
                        Text(e.optString("title"), Modifier.weight(1f), fontSize = 14.sp)
                        Text(if (e.optBoolean("sport")) "🏋️ Sport" else e.optString("min"), fontSize = 12.sp,
                            color = if (e.optBoolean("sport")) Fresh.Leaf else Fresh.Soft)
                    }
                }
                Hint("Als Sport zählt ein Termin, wenn eines dieser Wörter im Titel steht: ${settings.sportKeywords}")
            }
            Block("🧮  So entsteht dein Kalorienziel", Fresh.Leaf) {
                val t = d.optJSONArray("targets") ?: JSONArray()
                for (i in 0 until t.length()) t.getJSONObject(i).let { x ->
                    var open by remember { mutableStateOf(i == 0) }
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(Fresh.Mint)
                        .clickable { open = !open }.padding(12.dp)) {
                        Row {
                            Text(x.optString("day"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text("${x.optInt("kcal")} kcal", color = Fresh.Forest, fontWeight = FontWeight.Bold)
                        }
                        if (open) {
                            Text(
                                "Grundumsatz ${x.optInt("bmr")} + Alltag ${x.optInt("everyday")} + Sport ${x.optInt("sport")}" +
                                    (if (x.optInt("extra") > 0) " + Termin ${x.optInt("extra")}" else "") + " = ${x.optInt("kcal")} kcal · Protein ${x.optInt("protein")} g",
                                fontSize = 13.sp, color = Fresh.Ink, modifier = Modifier.padding(top = 6.dp),
                            )
                            Text(x.optString("how"), fontSize = 12.sp, color = Fresh.Soft, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            Block("🧾  Einkäufe (REWE-eBons)", Fresh.Citrus) { Text(d.optString("ebons"), fontSize = 13.sp) }
            Block("🥫  Vorrat", Fresh.Lime) { Text(d.optString("pantry"), fontSize = 13.sp) }
            Block("⚙️  Deine Vorgaben", Fresh.Leaf) {
                Rows(d.optJSONArray("settings"))
                Hint("KI: " + d.optString("model"))
            }
        }

        // Rezeptbuch
        Block("📖  Dein Rezeptbuch (${book.size})", Fresh.Citrus) {
            Text(
                "Deine Lieblingsrezepte, dauerhaft auf dem Handy. So kommen sie rein: In YAZIO das Rezept öffnen, Screenshots von Zutaten und Zubereitung machen und hier auswählen. Oder den Rezepttext einfügen. Der Plan nutzt diese Rezepte bevorzugt.",
                color = Fresh.Soft, fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
                if (uris.isEmpty()) return@rememberLauncherForActivityResult
                scope.launch {
                    var n = 0
                    for ((i, u) in uris.withIndex()) {
                        bookMsg = "Lese Rezept ${i + 1} von ${uris.size} …"
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val bytes = ctx.contentResolver.openInputStream(u)!!.use { it.readBytes() }
                                val mime = ctx.contentResolver.getType(u) ?: "image/jpeg"
                                RecipeBook(ctx).addFromJson(LlmClient(settings).extractRecipe(null, android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP), mime))
                            }.onSuccess { n++ }
                        }
                    }
                    bookMsg = "$n von ${uris.size} Rezepten gespeichert"
                    book = RecipeBook(ctx).all()
                }
            }
            val scanPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                if (!ok) { bookMsg = "Ohne Zugriff auf Fotos kann ich die Screenshots nicht durchsuchen."; return@rememberLauncherForActivityResult }
                scope.launch {
                    bookMsg = "Suche YAZIO-Screenshots …"
                    bookMsg = withContext(Dispatchers.IO) {
                        runCatching { ScreenshotScanner(ctx).run { p -> scope.launch { bookMsg = p } } }.getOrElse { "Suche abgebrochen: ${it.message?.take(100)}" }
                    }
                    book = RecipeBook(ctx).all()
                }
            }
            val packPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                bookMsg = "Importiere Rezeptpaket …"
                scope.launch {
                    bookMsg = withContext(Dispatchers.IO) {
                        runCatching { ctx.contentResolver.openInputStream(uri)!!.use { RecipeBook(ctx).importPack(it) } }
                            .fold({ "✓ $it Rezepte mit Foto übernommen" }, { "Import fehlgeschlagen: ${it.message?.take(100)}" })
                    }
                    book = RecipeBook(ctx).all()
                }
            }
            GradientButton("📦  Rezeptpaket importieren (ZIP)", brush = Fresh.Sunrise) { packPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
            Spacer(Modifier.height(8.dp))
            GradientButton("🔎  Screenshots automatisch durchsuchen") {
                scanPerm.launch(if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_IMAGES else android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            Spacer(Modifier.height(8.dp))
            var pasteOpen by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(shape = RoundedCornerShape(14.dp), onClick = { imgPicker.launch("image/*") }) { Text("📷 Bilder wählen") }
                OutlinedButton(shape = RoundedCornerShape(14.dp), onClick = { pasteOpen = true }) { Text("📋 Text einfügen") }
            }
            if (pasteOpen) {
                var txt by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { pasteOpen = false },
                    title = { Text("Rezept einfügen") },
                    text = { OutlinedTextField(txt, { txt = it }, minLines = 6, placeholder = { Text("Rezepttext hier einfügen") }) },
                    confirmButton = {
                        TextButton({
                            pasteOpen = false; bookMsg = "Lese Rezept …"
                            scope.launch {
                                bookMsg = withContext(Dispatchers.IO) {
                                    runCatching { RecipeBook(ctx).addFromJson(LlmClient(settings).extractRecipe(txt, null)) }
                                        .fold({ "„$it“ gespeichert" }, { "Konnte das Rezept nicht lesen: ${it.message?.take(80)}" })
                                }
                                book = RecipeBook(ctx).all()
                            }
                        }) { Text("Speichern") }
                    },
                    dismissButton = { TextButton({ pasteOpen = false }) { Text("Abbrechen") } },
                )
            }
            if (bookMsg.isNotBlank()) Hint(bookMsg)
            book.forEach { r ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp))
                        .clickable { RecipeActivity.open(ctx, RecipeBook(ctx).asMeal(r), "Rezeptbuch") }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val thumb = remember(r.image) { r.image?.let { p -> runCatching {
                        android.graphics.BitmapFactory.decodeFile(p, android.graphics.BitmapFactory.Options().apply { inSampleSize = 6 })?.asImageBitmap() }.getOrNull() } }
                    if (thumb != null) androidx.compose.foundation.Image(thumb, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
                    else Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Fresh.Peach), contentAlignment = Alignment.Center) {
                        Text(mealEmoji(r.name), fontSize = 20.sp)
                    }
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(r.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${r.kcal ?: "?"} kcal · ${r.proteinG ?: "?"} g Protein" + (r.minutes?.let { " · $it min" } ?: "") +
                            (if (r.steps.isEmpty()) " · ohne Zubereitung" else ""), color = Fresh.Soft, fontSize = 12.sp)
                    }
                    Text("›", color = Fresh.Soft, fontSize = 20.sp)
                }
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

/** Live: Health Connect und Kalender jetzt lesen, Ziele für heute bis in 6 Tagen rechnen. */
private suspend fun liveInputs(ctx: Context): JSONObject {
    val s = Settings(ctx)
    val health = s.withManualBody(runCatching { HealthRepo(ctx).snapshot() }.getOrElse { HealthRepo.Snapshot(null, null, null, null, null, null, null, emptyList()) })
    val from = LocalDate.now(); val to = from.plusDays(6)
    val events = runCatching { CalendarRepo(ctx).events(from, to, s.sportKeywords.split(",")) }.getOrElse { emptyList() }
    val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
    return Inputs.build(ctx, health, events, Energy.targets(days, events, health, s), s)
}

@Composable
private fun Block(title: String, accent: Color, content: @Composable ColumnScope.() -> Unit) =
    FreshCard(accent = accent) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        content()
    }

@Composable
private fun Rows(a: JSONArray?) {
    if (a == null) return
    for (i in 0 until a.length()) a.getJSONObject(i).let { r ->
        val missing = r.optString("v") == "fehlt"
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(r.optString("l"), fontSize = 14.sp)
                if (r.optString("i").isNotBlank()) Text(r.optString("i"), color = Fresh.Soft, fontSize = 11.sp)
            }
            Text(r.optString("v"), fontWeight = FontWeight.Bold, color = if (missing) Fresh.Berry else Fresh.Forest,
                fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp).widthIn(max = 200.dp))
        }
    }
}

@Composable
private fun Expandable(title: String, a: JSONArray?) {
    if (a == null || a.length() == 0) return
    var open by remember { mutableStateOf(false) }
    Text((if (open) "˄ " else "˅ ") + title, color = Fresh.Leaf, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp).clickable { open = !open })
    if (open) for (i in 0 until a.length()) Text(a.getString(i), fontSize = 12.sp, color = Fresh.Ink, modifier = Modifier.padding(top = 3.dp))
}

@Composable
private fun Hint(t: String) = Text(t, color = Fresh.Soft, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))

/** Gewicht und Körperfett von Hand eintragen, falls die Waage nicht an Health Connect liefert. */
@Composable
private fun ManualBody(settings: Settings, onSaved: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var w by remember { mutableStateOf(settings.manualWeight) }
    var f by remember { mutableStateOf(settings.manualFat) }
    Text((if (open) "˄ " else "˅ ") + "Von der Waage ablesen und eintragen", color = Fresh.Leaf, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp).clickable { open = !open })
    if (open) {
        Hint("AFit Health gibt die Werte nicht an Health Connect weiter. Trag sie nach dem Wiegen hier ein, die App nutzt sie, bis echte Waagen-Daten kommen.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            OutlinedTextField(w, { w = it }, label = { Text("Gewicht kg") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
            OutlinedTextField(f, { f = it }, label = { Text("Körperfett %") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
        }
        Button(
            onClick = {
                settings.manualWeight = w.trim(); settings.manualFat = f.trim()
                settings.manualDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                open = false; onSaved()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Fresh.Leaf), shape = RoundedCornerShape(14.dp),
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
        ) { Text("Speichern") }
    }
}
