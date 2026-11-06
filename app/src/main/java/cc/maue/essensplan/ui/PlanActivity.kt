package cc.maue.essensplan.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import cc.maue.essensplan.data.PlanGenerator
import cc.maue.essensplan.data.Settings
import cc.maue.essensplan.data.Updater
import cc.maue.essensplan.widget.PlanWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/** Hauptmenü: Tageskalender mit allen Mahlzeiten, Einkauf, neuer Plan. Einstellungen hinter dem Zahnrad. */
class PlanActivity : ComponentActivity() {

    private var openNewPlan by mutableStateOf(false)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = Settings(this)
        openNewPlan = intent.getBooleanExtra("new_plan", false)

        setContent {
            FreshTheme {
                var plan by remember { mutableStateOf(runCatching { JSONObject(settings.lastPlan) }.getOrNull()) }
                var tab by remember { mutableIntStateOf(0) }
                var update by remember { mutableStateOf<Updater.Info?>(null) }
                var busy by remember { mutableStateOf(false) }
                var status by remember { mutableStateOf("") }
                LaunchedEffect(Unit) { update = withContext(Dispatchers.IO) { Updater.check(this@PlanActivity) } }

                fun generate(from: LocalDate, to: LocalDate, bring: Boolean) {
                    busy = true; openNewPlan = false
                    lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                PlanGenerator(this@PlanActivity).run(from, to, bring) { s -> lifecycleScope.launch { status = s } }
                            }
                            plan = runCatching { JSONObject(settings.lastPlan) }.getOrNull()
                            status = ""; tab = 0
                            PlanWidget().updateAll(this@PlanActivity)
                        } catch (e: Exception) {
                            status = "Hat nicht geklappt: ${e.message}"
                        } finally { busy = false }
                    }
                }

                Scaffold(
                    containerColor = Fresh.Bg,
                    bottomBar = {
                        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                            listOf("🗓️" to "Plan", "🛒" to "Einkauf", "🥫" to "Vorrat").forEachIndexed { i, (ic, label) ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { if (i == 2) startActivity(Intent(this@PlanActivity, VorratActivity::class.java)) else tab = i },
                                    icon = { Text(ic, fontSize = 22.sp) }, label = { Text(label) },
                                    colors = NavigationBarItemDefaults.colors(indicatorColor = Fresh.Mint, selectedTextColor = Fresh.Forest),
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = { if (!busy) openNewPlan = true },
                            containerColor = Fresh.Leaf, contentColor = Color.White, shape = RoundedCornerShape(20.dp),
                            icon = { if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Text("✨") },
                            text = { Text(if (busy) "Plane …" else "Neuer Plan", fontWeight = FontWeight.SemiBold) },
                        )
                    },
                ) { pad ->
                    Column(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
                        TopBar(onSettings = { startActivity(Intent(this@PlanActivity, MainActivity::class.java)) })
                        if (busy || status.isNotBlank()) StatusLine(busy, status)
                        update?.let { u ->
                            UpdateBanner(u.version) { lifecycleScope.launch(Dispatchers.IO) { Updater.install(this@PlanActivity, u) } }
                        }
                        when (tab) {
                            0 -> DayCalendar(plan) { meal, label -> RecipeActivity.open(this@PlanActivity, meal, label) }
                            else -> ShoppingTab(plan,
                                onDone = { PlanGenerator(this@PlanActivity).markShoppingDone(); status = "Einkauf ist im Vorrat gebucht ✓" },
                                onImport = { startActivity(Intent(this@PlanActivity, ImportActivity::class.java)) })
                        }
                    }
                }

                if (openNewPlan) NewPlanSheet(onDismiss = { openNewPlan = false }, onGo = ::generate)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("new_plan", false)) openNewPlan = true
    }
}

// ---------------- Kopf ----------------

@Composable
private fun TopBar(onSettings: () -> Unit) {
    val today = LocalDate.now()
    val hello = when (LocalTime.now().hour) { in 5..10 -> "Guten Morgen"; in 11..17 -> "Hallo"; else -> "Guten Abend" }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).background(Fresh.Hero)
            .statusBarsPadding().padding(start = 22.dp, end = 12.dp, top = 14.dp, bottom = 20.dp)
    ) {
        Column {
            Text("$hello 🌿", color = Color.White.copy(alpha = .85f), fontSize = 15.sp)
            Text(
                today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.GERMAN) + ", " + today.dayOfMonth + ". " +
                    today.month.getDisplayName(TextStyle.FULL, Locale.GERMAN),
                color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
            )
        }
        Box(
            Modifier.align(Alignment.TopEnd).size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f)).clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) { Text("⚙️", fontSize = 20.sp) }
    }
}

@Composable
private fun StatusLine(busy: Boolean, status: String) =
    Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Fresh.Leaf)
        Text(status, color = Fresh.Soft, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
    }

@Composable
private fun UpdateBanner(version: String, onClick: () -> Unit) =
    Row(
        Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Fresh.Sunrise)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⬆️", fontSize = 20.sp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("Version $version ist da", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Tippen zum Aktualisieren", color = Color.White.copy(alpha = .9f), fontSize = 12.sp)
        }
    }

// ---------------- Tageskalender ----------------

@Composable
private fun DayCalendar(plan: JSONObject?, onMeal: (JSONObject, String) -> Unit) {
    val days = plan?.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
    val today = LocalDate.now().toString()
    // Heute zuerst, vergangene Tage ans Ende
    val ordered = days.sortedWith(compareBy({ it.optString("date") < today }, { it.optString("date") }))
    var sel by remember(plan) { mutableIntStateOf(0) }

    if (ordered.isEmpty()) {
        EmptyState(); return
    }
    val day = ordered[sel.coerceIn(0, ordered.lastIndex)]

    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(ordered) { i, d ->
            val date = runCatching { LocalDate.parse(d.optString("date")) }.getOrNull()
            val active = i == sel
            Column(
                Modifier.width(62.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (active) Fresh.Leaf else Color.White)
                    .border(1.dp, if (d.optString("date") == today && !active) Fresh.Leaf else Color.Transparent, RoundedCornerShape(20.dp))
                    .clickable { sel = i }.padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (d.optString("date") == today) "Heute" else date?.dayOfWeek?.getDisplayName(TextStyle.SHORT, Locale.GERMAN) ?: "",
                    color = if (active) Color.White.copy(alpha = .85f) else Fresh.Soft, fontSize = 12.sp,
                )
                Text("${date?.dayOfMonth ?: "?"}", color = if (active) Color.White else Fresh.Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        val meals = day.optJSONArray("meals") ?: JSONArray()
        val eaten = (0 until meals.length()).sumOf { meals.getJSONObject(it).optInt("kcal") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill("kcal Ziel", "${day.optInt("kcal")}")
            StatPill("geplant", "$eaten", tint = Fresh.Forest)
            StatPill("Protein", "${day.optInt("protein")} g", tint = Fresh.Citrus, bg = Fresh.Peach)
        }
        if (day.optString("note").isNotBlank())
            Text(day.optString("note"), color = Fresh.Soft, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
        Spacer(Modifier.height(14.dp))

        val now = LocalTime.now()
        val isToday = day.optString("date") == today
        for (j in 0 until meals.length()) {
            val m = meals.getJSONObject(j)
            val time = runCatching { LocalTime.parse(m.optString("uhrzeit").padStart(5, '0')) }.getOrNull()
            val past = isToday && time != null && time.isBefore(now.minusHours(1))
            val next = isToday && !past && (j == 0 || runCatching {
                LocalTime.parse(meals.getJSONObject(j - 1).optString("uhrzeit").padStart(5, '0')).isBefore(now.minusHours(1))
            }.getOrDefault(false))
            TimelineItem(m, last = j == meals.length() - 1, past = past, next = next) { onMeal(m, day.optString("label")) }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun TimelineItem(m: JSONObject, last: Boolean, past: Boolean, next: Boolean, onClick: () -> Unit) {
    val snack = m.optString("name").contains("snack", true)
    Row(Modifier.height(IntrinsicSize.Min)) {
        // Zeitachse
        Column(Modifier.width(54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(m.optString("uhrzeit"), color = if (next) Fresh.Leaf else Fresh.Soft, fontSize = 13.sp, fontWeight = if (next) FontWeight.Bold else FontWeight.Normal)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.size(12.dp).clip(CircleShape).background(if (past) Fresh.Mint else if (next) Fresh.Citrus else Fresh.Leaf))
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(Fresh.Mint))
        }
        // Karte
        Row(
            Modifier.weight(1f).padding(bottom = 12.dp).clip(RoundedCornerShape(22.dp))
                .background(if (next) Color.White else if (past) Color.White.copy(alpha = .6f) else Color.White)
                .border(if (next) 2.dp else 0.dp, if (next) Fresh.Citrus else Color.Transparent, RoundedCornerShape(22.dp))
                .clickable(onClick = onClick).padding(if (snack) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(if (snack) 42.dp else 54.dp).clip(RoundedCornerShape(16.dp)).background(if (snack) Fresh.Peach else Fresh.Mint),
                contentAlignment = Alignment.Center,
            ) { Text(mealEmoji(m.optString("name"), m.optString("rezept")), fontSize = if (snack) 20.sp else 28.sp) }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    (if (next) "Als Nächstes · " else "") + m.optString("name"),
                    color = if (next) Fresh.Citrus else Fresh.Soft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(m.optString("rezept"), style = MaterialTheme.typography.titleMedium, color = if (past) Fresh.Soft else Fresh.Ink,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${m.optInt("kcal")} kcal · ${m.optInt("protein_g")} g Protein" + (if (m.optInt("dauer_min") > 0) " · ${m.optInt("dauer_min")} min" else ""),
                    color = Fresh.Leaf, fontSize = 12.sp,
                )
            }
            Text("›", color = Fresh.Soft, fontSize = 24.sp)
        }
    }
}

@Composable
private fun EmptyState() =
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🥗", fontSize = 64.sp)
        Text("Noch kein Plan", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
        Text("Tipp unten auf „Neuer Plan“. Dann steht hier jeder Tag mit allen Gerichten und Snacks.", color = Fresh.Soft,
            modifier = Modifier.padding(top = 6.dp))
    }

// ---------------- Einkauf ----------------

@Composable
private fun ShoppingTab(plan: JSONObject?, onDone: () -> Unit, onImport: () -> Unit) {
    val shop = plan?.optJSONArray("shopping")
    val checked = remember(plan) { mutableStateListOf<Int>() }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FreshCard(accent = Fresh.Citrus) {
            Text("🛒  Einkaufsliste", style = MaterialTheme.typography.headlineSmall)
            Text("Vorrat ist schon abgezogen", color = Fresh.Soft, fontSize = 12.sp)
            if (shop == null || shop.length() == 0) Text("Alles da. Nichts zu kaufen.", color = Fresh.Soft, modifier = Modifier.padding(top = 8.dp))
            else for (i in 0 until shop.length()) shop.getJSONObject(i).let {
                val ok = i in checked
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp))
                        .clickable { if (ok) checked.remove(i) else checked.add(i) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (ok) Fresh.Leaf else Fresh.Peach), contentAlignment = Alignment.Center) {
                        if (ok) Text("✓", color = Color.White, fontSize = 12.sp)
                    }
                    Text(it.optString("n"), Modifier.padding(start = 12.dp).weight(1f), color = if (ok) Fresh.Soft else Fresh.Ink)
                    Text(it.optString("s"), color = Fresh.Soft, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            GradientButton("✅  Einkauf erledigt, in den Vorrat", onClick = onDone)
        }
        if (plan?.optString("rec").orEmpty().isNotBlank()) FreshCard(accent = Fresh.Lime) {
            Text("🏪  Wo einkaufen?", style = MaterialTheme.typography.headlineSmall)
            Text(plan!!.optString("rec"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
        FreshCard {
            Text("🧾  Aus REWE-eBons lernen", style = MaterialTheme.typography.titleMedium)
            Text("eBon-PDFs teilen, damit der Plan kennt, was du wirklich kaufst.", color = Fresh.Soft, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onImport, shape = RoundedCornerShape(16.dp)) { Text("eBons importieren") }
        }
        val hints = listOf(plan?.optString("hints").orEmpty()) +
            (plan?.optJSONArray("warnings")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList())
        if (hints.any { it.isNotBlank() })
            Text(hints.filter { it.isNotBlank() }.joinToString("\n"), color = Fresh.Soft, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
        Spacer(Modifier.height(90.dp))
    }
}

// ---------------- Neuer Plan ----------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NewPlanSheet(onDismiss: () -> Unit, onGo: (LocalDate, LocalDate, Boolean) -> Unit) {
    var from by remember { mutableStateOf(LocalDate.now()) }
    var to by remember { mutableStateOf(LocalDate.now().plusDays(2)) }
    var bring by remember { mutableStateOf(true) }
    var picker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Fresh.Bg) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Neuer Plan", style = MaterialTheme.typography.headlineSmall)
            Text("Für wie lange? Bei Reisen einfach nur bis zur Abfahrt planen.", color = Fresh.Soft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Heute" to 0L, "Morgen" to 1L, "3 Tage" to 2L, "Woche" to 6L).forEach { (label, d) ->
                    val start = LocalDate.now()
                    val sel = from == start && to == start.plusDays(d)
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (sel) Fresh.Leaf else Color.White)
                            .clickable { from = start; to = start.plusDays(d) }.padding(horizontal = 18.dp, vertical = 10.dp)
                    ) { Text(label, color = if (sel) Color.White else Fresh.Ink, fontWeight = FontWeight.SemiBold) }
                }
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable { picker = true }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) { Text("📅 Eigener Zeitraum", color = Fresh.Ink) }
            }
            Text("${from.dayOfMonth}.${from.monthValue}. bis ${to.dayOfMonth}.${to.monthValue}.", color = Fresh.Forest, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(bring, { bring = it }, colors = SwitchDefaults.colors(checkedTrackColor = Fresh.Leaf))
                Text("Einkaufsliste direkt an Bring", Modifier.padding(start = 10.dp))
            }
            GradientButton("✨  Plan erstellen") { onGo(from, to, bring) }
        }
    }

    if (picker) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = from.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            initialSelectedEndDateMillis = to.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(onDismissRequest = { picker = false }, confirmButton = {
            TextButton({
                state.selectedStartDateMillis?.let { from = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                to = (state.selectedEndDateMillis ?: state.selectedStartDateMillis)
                    ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: from
                picker = false
            }) { Text("Übernehmen") }
        }) { DateRangePicker(state, Modifier.height(480.dp)) }
    }
}
