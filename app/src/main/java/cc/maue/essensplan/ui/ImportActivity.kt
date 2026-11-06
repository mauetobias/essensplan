package cc.maue.essensplan.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import cc.maue.essensplan.data.PurchaseHistory
import cc.maue.essensplan.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * REWE-eBons einlesen. Zwei Wege:
 *  1. In Gmail einen eBon öffnen → PDF-Anhang → Teilen → Essensplan
 *  2. Hier mehrere PDFs auf einmal auswählen (z.B. aus dem Download-Ordner)
 */
class ImportActivity : ComponentActivity() {

    private var status by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var refresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedUris(intent).takeIf { it.isNotEmpty() }?.let { process(it) }

        setContent {
            FreshTheme {
                val history = remember(refresh) { PurchaseHistory(this) }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) process(it) }
                Column(Modifier.fillMaxSize().background(Fresh.Bg).verticalScroll(rememberScrollState())) {
                    HeroHeader("Einkaufsgedächtnis", "${history.receiptCount()} Kassenbons gelernt", "🧾")
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FreshCard(accent = Fresh.Leaf) {
                            Text("So lernt die App, was du kaufst", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Deine REWE-eBons kommen per Mail. Öffne einen in Gmail, tippe beim PDF auf Teilen und wähle Essensplan. " +
                                    "Oder lade mehrere PDFs herunter und wähle sie hier alle auf einmal aus.",
                                color = Fresh.Soft, modifier = Modifier.padding(top = 6.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            GradientButton(if (busy) "Lese Bons …" else "PDFs auswählen", enabled = !busy) { picker.launch(arrayOf("application/pdf")) }
                            if (status.isNotBlank()) Text(status, color = Fresh.Soft, modifier = Modifier.padding(top = 8.dp))
                        }
                        val top = history.top()
                        if (top.isNotEmpty()) FreshCard(accent = Fresh.Citrus) {
                            Text("Das kaufst du am häufigsten", style = MaterialTheme.typography.titleMedium)
                            top.forEach { (n, c) ->
                                Row(Modifier.padding(top = 6.dp)) {
                                    Text(n, Modifier.weight(1f)); Text("${c}x", color = Fresh.Leaf)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedUris(intent).takeIf { it.isNotEmpty() }?.let { process(it) }
    }

    @Suppress("DEPRECATION")
    private fun sharedUris(i: Intent?): List<Uri> = when (i?.action) {
        Intent.ACTION_SEND -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)
        )
        Intent.ACTION_SEND_MULTIPLE ->
            (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()
        else -> emptyList()
    }

    private fun process(uris: List<Uri>) {
        busy = true
        lifecycleScope.launch {
            val h = PurchaseHistory(this@ImportActivity); val s = Settings(this@ImportActivity)
            var items = 0; var done = 0; var skipped = 0; var failed = 0
            for (u in uris) {
                status = "Bon ${done + skipped + failed + 1} von ${uris.size} …"
                withContext(Dispatchers.IO) {
                    runCatching { contentResolver.openInputStream(u)!!.use { it.readBytes() } }
                        .mapCatching { h.importPdf(it, s) }
                        .onSuccess { if (it == 0) skipped++ else { done++; items += it } }
                        .onFailure { failed++ }
                }
            }
            status = "$done Bons gelernt, $items Artikel" +
                (if (skipped > 0) ", $skipped kannte ich schon" else "") + (if (failed > 0) ", $failed nicht lesbar" else "")
            busy = false; refresh++
        }
    }
}
