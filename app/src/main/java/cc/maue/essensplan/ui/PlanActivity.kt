package cc.maue.essensplan.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
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
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Startbildschirm: Zeitraum wählen, Plan erstellen, Plan als Karten ansehen. */
class PlanActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = Settings(this)
        setContent {
            FreshTheme {
                var from by remember { mutableStateOf(LocalDate.now()) }
                var to by remember { mutableStateOf(LocalDate.now().plusDays(2)) }
                var bring by remember { mutableStateOf(true) }
                var status by remember { mutableStateOf("") }
                var busy by remember { mutableStateOf(false) }
                var showPicker by remember { mutableStateOf(false) }
                var plan by remember { mutableStateOf(runCatching { JSONObject(settings.lastPlan) }.getOrNull()) }
                var update by remember { mutableStateOf<Updater.Info?>(null) }

                LaunchedEffect(Unit) { update = withContext(Dispatchers.IO) { Updater.check(this@PlanActivity) } }

                Column(Modifier.fillMaxSize().background(Fresh.Bg).verticalScroll(rememberScrollState())) {
                    HeroHeader("Essensplan", "Frisch planen. Schlau einkaufen.") {
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Heute" to 0L, "3 Tage" to 2L, "Woche" to 6L).forEach { (label, d) ->
                                val sel = from == LocalDate.now() && to == LocalDate.now().plusDays(d)
                                Box(
                                    Modifier.clip(RoundedCornerShape(50)).background(if (sel) Color.White else Color.White.copy(alpha = .18f))
                                        .clickable { from = LocalDate.now(); to = LocalDate.now().plusDays(d) }
                                        .padding(horizontal = 16.dp, vertical = 9.dp)
                                ) { Text(label, color = if (sel) Fresh.Forest else Color.White, fontWeight = FontWeight.SemiBold) }
                            }
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .18f))
                                    .clickable { showPicker = true }.padding(horizontal = 14.dp, vertical = 9.dp)
                            ) { Text("📅", color = Color.White) }
                        }
                        Text(
                            "${from.dayOfMonth}.${from.monthValue}. bis ${to.dayOfMonth}.${to.monthValue}.",
                            color = Color.White.copy(alpha = .9f), modifier = Modifier.padding(top = 10.dp, start = 4.dp)
                        )
                    }

                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        update?.let { u ->
                            FreshCard(accent = Fresh.Citrus) {
                                Text("Neue Version ${u.version} ist da", style = MaterialTheme.typography.titleMedium)
                                Text("Ein Tipp, und sie wird geladen und installiert.", color = Fresh.Soft)
                                Spacer(Modifier.height(10.dp))
                                GradientButton("Jetzt aktualisieren", brush = Fresh.Sunrise) {
                                    lifecycleScope.launch(Dispatchers.IO) { Updater.install(this@PlanActivity, u) }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                            Switch(bring, { bring = it })
                            Text("Liste direkt an Bring senden", Modifier.padding(start = 10.dp), color = Fresh.Ink)
                        }

                        GradientButton(if (busy) "Plane …" else "Plan erstellen", enabled = !busy) {
                            busy = true
                            lifecycleScope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        PlanGenerator(this@PlanActivity).run(from, to, bring) { s -> lifecycleScope.launch { status = s } }
                                    }
                                    plan = runCatching { JSONObject(settings.lastPlan) }.getOrNull()
                                    status = ""
                                    PlanWidget().updateAll(this@PlanActivity)
                                } catch (e: Exception) {
                                    status = "Hat nicht geklappt: ${e.message}"
                                } finally { busy = false }
                            }
                        }
                        AnimatedVisibility(busy || status.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Fresh.Leaf)
                                Text(status, color = Fresh.Soft, modifier = Modifier.padding(start = 10.dp))
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuickChip("✅  Einkauf erledigt") {
                                PlanGenerator(this@PlanActivity).markShoppingDone(); status = "Einkauf ist im Vorrat gebucht"
                            }
                            QuickChip("🥫  Vorrat") { startActivity(Intent(this@PlanActivity, VorratActivity::class.java)) }
                            QuickChip("🧾  REWE eBons") { startActivity(Intent(this@PlanActivity, ImportActivity::class.java)) }
                            QuickChip("⚙️  Einstellungen") { startActivity(Intent(this@PlanActivity, MainActivity::class.java)) }
                        }

                        plan?.let { PlanView(it) }
                        Spacer(Modifier.height(30.dp))
                    }
                }

                if (showPicker) {
                    val state = rememberDateRangePickerState(
                        initialSelectedStartDateMillis = from.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                        initialSelectedEndDateMillis = to.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                    )
                    DatePickerDialog(onDismissRequest = { showPicker = false }, confirmButton = {
                        TextButton({
                            state.selectedStartDateMillis?.let { from = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                            to = (state.selectedEndDateMillis ?: state.selectedStartDateMillis)
                                ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: from
                            showPicker = false
                        }) { Text("Übernehmen") }
                    }) { DateRangePicker(state, Modifier.height(480.dp)) }
                }
            }
        }
    }
}

@Composable
private fun QuickChip(text: String, onClick: () -> Unit) =
    Box(
        Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) { Text(text, color = Fresh.Ink, fontSize = 14.sp) }

private val mealEmoji = listOf("frühstück" to "🥣", "mittag" to "🥗", "abend" to "🍲", "snack" to "🥨", "pre" to "⚡", "post" to "💪", "vor" to "⚡", "nach" to "💪")

@Composable
private fun PlanView(p: JSONObject) {
    val days = p.optJSONArray("days") ?: return
    for (i in 0 until days.length()) {
        val d = days.getJSONObject(i)
        FreshCard(accent = Fresh.Leaf) {
            Text(d.optString("label"), style = MaterialTheme.typography.headlineSmall)
            if (d.optString("note").isNotBlank()) Text(d.optString("note"), color = Fresh.Soft, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("kcal Ziel", d.optInt("kcal").toString())
                StatPill("Protein", "${d.optInt("protein")} g", tint = Fresh.Citrus, bg = Fresh.Peach)
            }
            val meals = d.optJSONArray("meals")
            if (meals != null) for (j in 0 until meals.length()) {
                val m = meals.getJSONObject(j)
                val emoji = mealEmoji.firstOrNull { m.optString("name").lowercase().contains(it.first) }?.second ?: "🍽️"
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Fresh.Mint), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 20.sp) }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("${m.optString("uhrzeit")}  ·  ${m.optString("name")}", color = Fresh.Soft, fontSize = 12.sp)
                        Text(m.optString("rezept"), style = MaterialTheme.typography.titleMedium)
                        Text("${m.optInt("kcal")} kcal  ·  ${m.optInt("protein_g")} g Protein", color = Fresh.Leaf, fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    val shop = p.optJSONArray("shopping")
    FreshCard(accent = Fresh.Citrus) {
        Text("🛒  Einkaufsliste", style = MaterialTheme.typography.headlineSmall)
        if (shop == null || shop.length() == 0) Text("Alles da. Nichts zu kaufen.", color = Fresh.Soft)
        else for (i in 0 until shop.length()) shop.getJSONObject(i).let {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Fresh.Citrus))
                Text(it.optString("n"), Modifier.padding(start = 10.dp).weight(1f))
                Text(it.optString("s"), color = Fresh.Soft, fontSize = 13.sp)
            }
        }
    }
    if (p.optString("rec").isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        FreshCard(accent = Fresh.Lime) {
            Text("🏪  Wo einkaufen?", style = MaterialTheme.typography.headlineSmall)
            Text(p.optString("rec"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
    }
    val hints = listOf(p.optString("hints")) + (p.optJSONArray("warnings")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList())
    if (hints.any { it.isNotBlank() }) {
        Spacer(Modifier.height(12.dp))
        Text(hints.filter { it.isNotBlank() }.joinToString("\n"), color = Fresh.Soft, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
        Text("Geplant mit ${p.optString("provider")}", color = Fresh.Soft, fontSize = 11.sp, modifier = Modifier.padding(6.dp))
    }
}
