package cc.maue.essensplan.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import cc.maue.essensplan.data.PlanGenerator
import cc.maue.essensplan.widget.PlanWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Kleiner Dialog, der vom Widget-Knopf aus öffnet: Zeitraum wählen, Plan erstellen. */
class PlanActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var from by remember { mutableStateOf(LocalDate.now()) }
                var to by remember { mutableStateOf(LocalDate.now().plusDays(6)) }
                var bring by remember { mutableStateOf(true) }
                var status by remember { mutableStateOf("") }
                var result by remember { mutableStateOf("") }
                var busy by remember { mutableStateOf(false) }
                var showPicker by remember { mutableStateOf(false) }

                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Essensplan", style = MaterialTheme.typography.headlineSmall)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Nur heute" to 0L, "3 Tage" to 2L, "Woche" to 6L).forEach { (label, d) ->
                                FilterChip(selected = from == LocalDate.now() && to == LocalDate.now().plusDays(d),
                                    onClick = { from = LocalDate.now(); to = LocalDate.now().plusDays(d) },
                                    label = { Text(label) })
                            }
                        }
                        OutlinedButton(onClick = { showPicker = true }) { Text("Zeitraum: ${from.dayOfMonth}.${from.monthValue}. bis ${to.dayOfMonth}.${to.monthValue}.") }

                        Row { Checkbox(bring, { bring = it }); Text("Einkaufsliste an Bring senden", Modifier.padding(top = 12.dp)) }

                        Button(enabled = !busy, onClick = {
                            busy = true; result = ""
                            lifecycleScope.launch {
                                try {
                                    val out = withContext(Dispatchers.IO) {
                                        PlanGenerator(this@PlanActivity).run(from, to, bring) { s -> lifecycleScope.launch { status = s } }
                                    }
                                    status = "Fertig mit ${out.provider}" + (if (bring) ", ${out.bringAdded} Artikel in Bring" else "")
                                    result = out.text + (if (out.warnings.isNotEmpty()) "\n\nHinweise:\n" + out.warnings.joinToString("\n") else "")
                                    PlanWidget().updateAll(this@PlanActivity)
                                } catch (e: Exception) {
                                    status = "Fehler: ${e.message}"
                                } finally { busy = false }
                            }
                        }) { Text(if (busy) "Läuft …" else "Plan erstellen") }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = !busy, onClick = {
                                PlanGenerator(this@PlanActivity).markShoppingDone()
                                status = "Einkauf ist im Vorrat gebucht"
                            }) { Text("Einkauf erledigt") }
                            OutlinedButton({ startActivity(android.content.Intent(this@PlanActivity, VorratActivity::class.java)) }) { Text("Vorrat") }
                        }

                        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium)
                        if (result.isNotBlank()) Text(result, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
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
                        }) { Text("OK") }
                    }) { DateRangePicker(state, Modifier.height(480.dp)) }
                }
            }
        }
    }
}
