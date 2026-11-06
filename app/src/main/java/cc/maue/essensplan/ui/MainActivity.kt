package cc.maue.essensplan.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import cc.maue.essensplan.data.HealthRepo
import cc.maue.essensplan.data.Settings

/** Einrichtung: Berechtigungen, Schlüssel, Logins, Profil. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val s = Settings(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val calLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {}

                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Einrichtung", style = MaterialTheme.typography.headlineSmall)

                        Button({ hcLauncher.launch(HealthRepo.PERMISSIONS) }) { Text("Health Connect freigeben (Waage + Garmin)") }
                        Button({ calLauncher.launch(Manifest.permission.READ_CALENDAR) }) { Text("Kalender freigeben") }

                        Section("KI")
                        Field("Claude API-Key", s.claudeKey, secret = true) { s.claudeKey = it }
                        Field("Claude Modell", s.claudeModel) { s.claudeModel = it }
                        Field("Gemini API-Key (Reserve)", s.geminiKey, secret = true) { s.geminiKey = it }
                        Field("Gemini Modell", s.geminiModel) { s.geminiModel = it }

                        Section("YAZIO")
                        Field("E-Mail", s.yazioEmail) { s.yazioEmail = it }
                        Field("Passwort", s.yazioPassword, secret = true) { s.yazioPassword = it }

                        Section("Bring!")
                        Field("E-Mail", s.bringEmail) { s.bringEmail = it }
                        Field("Passwort", s.bringPassword, secret = true) { s.bringPassword = it }

                        Section("Profil")
                        Field("Geburtsjahr", s.birthYear, number = true) { s.birthYear = it }
                        Field("Größe in cm", s.heightCm, number = true) { s.heightCm = it }
                        Field("Geschlecht (m/w)", s.sex) { s.sex = it }
                        Field("Ziel", s.goal) { s.goal = it }
                        Field("Mag ich nicht / Unverträglichkeiten", s.dislikes) { s.dislikes = it }
                        Field("Sport-Stichwörter im Kalender (Komma)", s.sportKeywords) { s.sportKeywords = it }

                        Spacer(Modifier.height(8.dp))
                        Button({ startActivity(Intent(this@MainActivity, PlanActivity::class.java)) }) { Text("Plan jetzt erstellen") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) = Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))

@Composable
private fun Field(label: String, initial: String, secret: Boolean = false, number: Boolean = false, save: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    OutlinedTextField(
        value = v, onValueChange = { v = it; save(it) }, label = { Text(label) }, singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth(),
    )
}
