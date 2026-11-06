package cc.maue.essensplan.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.maue.essensplan.data.Pantry

/** Was ist zu Hause: Grundvorrat an/aus, Mengen korrigieren, Mindestbestand für "Immer da". */
class VorratActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pantry = Pantry(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var list by remember { mutableStateOf(pantry.items().toList()) }
                var newName by remember { mutableStateOf("") }
                fun update(f: (MutableList<Pantry.Item>) -> Unit) {
                    val l = pantry.items(); f(l); pantry.save(l); list = l.toList()
                }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Vorrat", style = MaterialTheme.typography.headlineSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(newName, { newName = it }, label = { Text("Artikel hinzufügen") }, modifier = Modifier.weight(1f), singleLine = true)
                            TextButton({ if (newName.isNotBlank()) { update { it += Pantry.Item(newName.trim(), 1.0, "Stück") }; newName = "" } }) { Text("+") }
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            item { Header("Immer da") }
                            items(list.filter { it.minQty > 0 }, key = { "a" + it.name }) { it ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${it.name}", Modifier.weight(1f))
                                    Stepper("${fmt(it.qty)} / ${fmt(it.minQty)} ${it.unit}",
                                        { update { l -> pantry.find(l, it.name)?.let { x -> x.qty = (x.qty - 1).coerceAtLeast(0.0) } } },
                                        { update { l -> pantry.find(l, it.name)?.let { x -> x.qty += 1 } } })
                                }
                            }
                            item { Header("Vorräte") }
                            items(list.filter { !it.staple && it.minQty == 0.0 }, key = { "v" + it.name }) { it ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(it.name, Modifier.weight(1f))
                                    Stepper("${fmt(it.qty)} ${it.unit}",
                                        { update { l -> pantry.find(l, it.name)?.let { x -> x.qty = (x.qty - step(x.unit)).coerceAtLeast(0.0) }; l.removeAll { x -> x.qty <= 0 && !x.staple && x.minQty == 0.0 } } },
                                        { update { l -> pantry.find(l, it.name)?.let { x -> x.qty += step(x.unit) } } })
                                }
                            }
                            item { Header("Grundvorrat (Haken weg = leer, kommt auf die Liste)") }
                            items(list.filter { it.staple }, key = { "s" + it.name }) { it ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(!it.empty, { v -> update { l -> pantry.find(l, it.name)?.empty = !v } })
                                    Text(it.name)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun step(unit: String) = if (unit == "g" || unit == "ml") 100.0 else 1.0
    private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else "%.1f".format(d)
}

@Composable private fun Header(t: String) = Text(t, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))

@Composable private fun Stepper(label: String, minus: () -> Unit, plus: () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
    TextButton(minus) { Text("−") }; Text(label); TextButton(plus) { Text("+") }
}
