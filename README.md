# Essensplan

Privates Android-Widget: ein Knopf auf dem Homescreen, Zeitraum wählen (nur heute, 3 Tage, Woche oder frei), und die App erstellt einen Essensplan plus Einkaufsliste und schickt sie direkt nach Bring.

## Was reinfließt

| Quelle | Weg | Wofür |
|---|---|---|
| MyBeo Waage | AFit Health → Health Connect | Gewicht, Körperfett → Grundumsatz (Katch-McArdle) |
| Garmin Forerunner 965 | Garmin Connect → Health Connect | aktive kcal, Schritte, Schlaf, Ruhepuls, letzte Einheiten |
| Google Kalender | direkt am Pixel | Sporttermine (Stichwörter wie CrossFit, WOD) erhöhen kcal und Protein; andere Termine (z.B. Messe) beeinflussen die Mahlzeitenplanung |
| YAZIO | inoffizielle App-Schnittstelle | deine Rezepte werden bevorzugt eingeplant |
| Claude, sonst Gemini | API | baut Plan und Liste; fällt Claude aus (Guthaben leer, Limit, Störung), übernimmt Gemini automatisch |
| Bring! | inoffizielle App-Schnittstelle | Einkaufsliste landet auf deiner Standardliste |

Die Kalorienziele rechnet die App selbst aus, die KI plant nur die Mahlzeiten drumherum. So bleiben die Zahlen nachvollziehbar (siehe `Energy.kt`).

## Einrichtung

1. Projekt in Android Studio öffnen, Gradle synchronisieren, auf das Pixel installieren.
2. **Health Connect**: Einstellungen → Health Connect → App-Berechtigungen.
   * **AFit Health** muss Gewicht und Körperfett *schreiben* dürfen. Falls AFit Health dort nicht auftaucht, unterstützt es Health Connect nicht; dann bitte melden, dann bauen wir eine manuelle Eingabe oder einen anderen Weg.
   * **Garmin Connect** muss Aktivitäten, Schlaf, Schritte und Kalorien *schreiben* dürfen (in Garmin Connect unter Einstellungen → Verbundene Apps → Health Connect).
3. App öffnen, „Health Connect freigeben“ und „Kalender freigeben“ antippen.
4. Schlüssel eintragen:
   * Claude: console.anthropic.com → API Keys
   * Gemini: aistudio.google.com → API-Schlüssel (privates Google-Konto)
5. YAZIO- und Bring-Login eintragen. Alles wird verschlüsselt nur auf dem Handy gespeichert.
6. Widget „Essensplan“ auf den Homescreen ziehen.

## Gut zu wissen

* YAZIO und Bring haben keine offiziellen Schnittstellen. Die App nutzt dieselben Wege wie deren eigene Apps. Wenn einer davon bricht, läuft der Plan trotzdem weiter und zeigt einen Hinweis.
* YAZIO mit Apple- oder Google-Login funktioniert nicht, es braucht E-Mail und Passwort.
* Modellnamen lassen sich in der Einrichtung ändern, falls neue Versionen kommen.
