package cc.maue.essensplan.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import cc.maue.essensplan.data.HealthRepo
import cc.maue.essensplan.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cc.maue.essensplan.data.BringClient
import cc.maue.essensplan.data.LlmClient
import cc.maue.essensplan.data.YazioClient

/** Einstellungen: Freigaben mit Status, Rhythmus, Märkte, Verbindungen (eingeklappt), Profil. */
class MainActivity : ComponentActivity() {

    /** Health-Connect-Freigabeseite direkt für diese App öffnen (falls der Dialog nicht erscheint). */
    private fun openHealthConnectPage() {
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= 34) add(Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").putExtra(Intent.EXTRA_PACKAGE_NAME, packageName))
            add(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        }
        for (i in intents) if (runCatching { startActivity(i) }.isSuccess) return
    }

    private fun openAppSettings() =
        startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val s = Settings(this)
        val health = HealthRepo(this)

        setContent {
            FreshTheme {
                val scope = rememberCoroutineScope()
                var hcOk by remember { mutableStateOf(false) }
                var calOk by remember { mutableStateOf(false) }
                var locOk by remember { mutableStateOf(false) }
                fun refresh() {
                    calOk = granted(Manifest.permission.READ_CALENDAR)
                    locOk = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
                    scope.launch { hcOk = runCatching { health.hasAllPermissions() }.getOrDefault(false) }
                }
                LifecycleResumeEffect(Unit) { refresh(); onPauseOrDispose { } }

                val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { got ->
                    if (!got.containsAll(HealthRepo.PERMISSIONS)) openHealthConnectPage()
                    refresh()
                }
                val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
                    // Android zeigt den Dialog nach zweimal Ablehnen nicht mehr: dann direkt zur App-Seite
                    if (res.values.any { !it }) openAppSettings()
                    refresh()
                }

                Column(Modifier.fillMaxSize().background(Fresh.Bg).verticalScroll(rememberScrollState())) {
                    HeroHeader("Einstellungen", "Einmal einrichten, dann läuft's", "⚙️")
                    Column(Modifier.padding(16.dp)) {

                        SectionTitle("Freigaben")
                        FreshCard {
                            PermissionRow("⚖️", "Waage + Garmin", "über Health Connect", hcOk) {
                                if (!health.available) openHealthConnectPage() else hcLauncher.launch(HealthRepo.PERMISSIONS)
                            }
                            PermissionRow("📅", "Kalender", "Sport und Termine erkennen", calOk) {
                                permLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR))
                            }
                            PermissionRow("📍", "Standort", "PLZ für Angebote", locOk) {
                                permLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
                            }
                            if (!hcOk) Text(
                                "Erscheint kein Dialog, öffnet sich die Health-Connect-Seite. Dort alles erlauben. " +
                                    "Und in Health Connect müssen AFit Health und Garmin Connect Daten schreiben dürfen.",
                                color = Fresh.Soft, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        SectionTitle("Verbindungen")
                        FreshCard {
                            Connection("✨", "Gemini", s.geminiKey.isNotBlank(), "aistudio.google.com/apikey", test = { LlmClient(s).testGemini() }) {
                                Field("API-Key", s.geminiKey, secret = true) { s.geminiKey = it }
                                Field("Modell (passt sich selbst an)", s.geminiModel) { s.geminiModel = it }
                            }
                            Connection("🤖", "Claude (optional, kostenpflichtig)", s.claudeKey.isNotBlank(), "console.anthropic.com/settings/keys", test = { LlmClient(s).testClaude() }) {
                                Field("API-Key", s.claudeKey, secret = true) { s.claudeKey = it }
                                Field("Modell", s.claudeModel) { s.claudeModel = it }
                            }
                            Connection("🛒", "Bring!", s.bringEmail.isNotBlank() && s.bringPassword.isNotBlank(), null, test = { BringClient(s.bringEmail, s.bringPassword).test() }) {
                                Field("E-Mail", s.bringEmail, type = ContentType.EmailAddress) { s.bringEmail = it }
                                Field("Passwort", s.bringPassword, secret = true, type = ContentType.Password) { s.bringPassword = it }
                            }
                            Text(
                                "Eintragen und auf „Speichern und verbinden“ tippen. Alles bleibt verschlüsselt auf dem Handy. Bei E-Mail und Passwort schlägt der Google Passwortmanager deine Logins vor. Gemini reicht, Claude ist nur Reserve.",
                                color = Fresh.Soft, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        SectionTitle("Mein Rhythmus")
                        FreshCard {
                            Field("Essensrhythmus", s.rhythm, lines = 4) { s.rhythm = it }
                            Field("Ziel", s.goal, lines = 2) { s.goal = it }
                            Field("Mag ich nicht / verträgt nicht", s.dislikes) { s.dislikes = it }
                            Field("Sport-Stichwörter im Kalender", s.sportKeywords) { s.sportKeywords = it }
                            Field("Lagerplatz", s.storage, lines = 3) { s.storage = it }
                        }

                        SectionTitle("Supermärkte")
                        FreshCard {
                            Field("Lieblingsmarkt", s.preferredStore) { s.preferredStore = it }
                            Field("Märkte in der Nähe (Komma)", s.stores) { s.stores = it }
                            Field("Wo liegt was", s.storeNotes, lines = 3) { s.storeNotes = it }
                            Field("PLZ (leer = Handystandort)", s.zip, number = true) { s.zip = it }
                        }

                        SectionTitle("Profil")
                        FreshCard {
                            Field("Geburtsjahr", s.birthYear, number = true) { s.birthYear = it }
                            Field("Größe in cm", s.heightCm, number = true) { s.heightCm = it }
                            Field("Geschlecht (m/w)", s.sex) { s.sex = it }
                        }
                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(emoji: String, title: String, sub: String, ok: Boolean, onClick: () -> Unit) =
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(if (ok) Fresh.Mint else Fresh.Peach)
            .clickable(enabled = !ok, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 22.sp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Fresh.Ink)
            Text(sub, color = Fresh.Soft, fontSize = 12.sp)
        }
        Text(if (ok) "✓ erlaubt" else "Erlauben ›", color = if (ok) Fresh.Leaf else Fresh.Citrus, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }

@Composable
private fun Connection(
    emoji: String, name: String, connected: Boolean, keyUrl: String?,
    test: () -> String, content: @Composable ColumnScope.() -> Unit,
) {
    var ok by remember { mutableStateOf(connected) }
    var open by remember { mutableStateOf(!connected) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { open = !open }.padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, fontSize = 20.sp)
            Text(name, Modifier.padding(start = 12.dp).weight(1f), fontWeight = FontWeight.SemiBold)
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (ok) Fresh.Leaf else Fresh.Citrus))
            Text(if (ok) " verbunden" else " fehlt", color = if (ok) Fresh.Leaf else Fresh.Citrus, fontSize = 12.sp)
            Text(if (open) "  ˄" else "  ˅", color = Fresh.Soft)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 4.dp)) {
                content()
                Button(
                    enabled = !busy, shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Fresh.Leaf),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    onClick = {
                        busy = true; msg = ""
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { runCatching { test() } }
                            busy = false
                            r.onSuccess { ok = true; msg = "✓ Gespeichert. $it"; delay(1200); open = false }
                             .onFailure { ok = false; msg = "Gespeichert, aber Verbindung klappt nicht: " + friendly(it) }
                        }
                    },
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Speichern und verbinden", fontWeight = FontWeight.SemiBold)
                }
                if (msg.isNotBlank()) Text(msg, color = if (ok) Fresh.Leaf else Fresh.Berry, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                if (keyUrl != null) TextButton({ uri.openUri("https://$keyUrl") }) { Text("Schlüssel holen: $keyUrl", color = Fresh.Leaf, fontSize = 12.sp) }
            }
        }
    }
}

/** Fehlermeldungen in verständliche Sätze übersetzen. */
private fun friendly(e: Throwable): String {
    val t = e.message.orEmpty()
    return when {
        t.contains("credit balance", true) -> "Kein Guthaben bei Claude. Nimm einfach Gemini."
        t.startsWith("HTTP 401") || t.startsWith("HTTP 403") || t.contains("API_KEY_INVALID") -> "Schlüssel oder Passwort falsch."
        t.startsWith("HTTP 400") && t.contains("invalid_grant") -> "E-Mail oder Passwort falsch."
        t.startsWith("HTTP 429") -> "Gerade zu viele Anfragen, gleich nochmal versuchen."
        e is java.net.UnknownHostException -> "Kein Internet."
        else -> t.take(140)
    }
}

@Composable
private fun Field(
    label: String, initial: String, secret: Boolean = false, number: Boolean = false, lines: Int = 1,
    type: ContentType? = null, save: (String) -> Unit,
) {
    var v by remember { mutableStateOf(initial) }
    OutlinedTextField(
        value = v, onValueChange = { v = it; save(it.trim()) }, label = { Text(label) },
        singleLine = lines == 1, minLines = 1, maxLines = lines.coerceAtLeast(1) + 2,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Fresh.Leaf, unfocusedBorderColor = Fresh.Mint, focusedLabelColor = Fresh.Leaf),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = when { number -> KeyboardType.Number; type == ContentType.EmailAddress -> KeyboardType.Email; secret -> KeyboardType.Password; else -> KeyboardType.Text },
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).then(if (type != null) Modifier.semantics { contentType = type } else Modifier),
    )
}
