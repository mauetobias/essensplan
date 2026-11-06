package cc.maue.essensplan.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import cc.maue.essensplan.data.HealthRepo
import cc.maue.essensplan.data.Settings

/** Einstellungen: Freigaben, Schlüssel, Logins, Märkte, Rhythmus, Profil. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val s = Settings(this)
        setContent {
            FreshTheme {
                val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
                val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {}

                Column(Modifier.fillMaxSize().background(Fresh.Bg).verticalScroll(rememberScrollState())) {
                    HeroHeader("Einstellungen", "Einmal einrichten, dann läuft's", "⚙️")
                    Column(Modifier.padding(16.dp)) {

                        SectionTitle("Freigaben")
                        FreshCard {
                            GradientButton("Waage + Garmin (Health Connect)") { hcLauncher.launch(HealthRepo.PERMISSIONS) }
                            Spacer(Modifier.height(10.dp))
                            GradientButton("Kalender + Standort", brush = Fresh.Sunrise) {
                                permLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.ACCESS_COARSE_LOCATION))
                            }
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

                        SectionTitle("KI")
                        FreshCard {
                            Field("Claude API-Key", s.claudeKey, secret = true) { s.claudeKey = it }
                            Field("Claude Modell", s.claudeModel) { s.claudeModel = it }
                            Field("Gemini API-Key (Reserve)", s.geminiKey, secret = true) { s.geminiKey = it }
                            Field("Gemini Modell", s.geminiModel) { s.geminiModel = it }
                        }

                        SectionTitle("Konten")
                        FreshCard {
                            Field("YAZIO E-Mail", s.yazioEmail) { s.yazioEmail = it }
                            Field("YAZIO Passwort", s.yazioPassword, secret = true) { s.yazioPassword = it }
                            Field("Bring! E-Mail", s.bringEmail) { s.bringEmail = it }
                            Field("Bring! Passwort", s.bringPassword, secret = true) { s.bringPassword = it }
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
private fun Field(label: String, initial: String, secret: Boolean = false, number: Boolean = false, lines: Int = 1, save: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    OutlinedTextField(
        value = v, onValueChange = { v = it; save(it) }, label = { Text(label) },
        singleLine = lines == 1, minLines = 1, maxLines = lines.coerceAtLeast(1) + 2,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Fresh.Leaf, unfocusedBorderColor = Fresh.Mint, focusedLabelColor = Fresh.Leaf),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    )
}
