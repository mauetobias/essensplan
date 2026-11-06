# Essensplan

Privates Android-Widget: ein Knopf auf dem Homescreen, Zeitraum wählen (nur heute, 3 Tage, Woche oder frei), und die App erstellt Essensplan und Einkaufsliste, gleicht mit dem Vorrat zu Hause ab, empfiehlt den günstigsten Supermarkt und schickt die Liste nach Bring.

## Installieren (ohne Android Studio)

Jeder Push auf `main` baut über GitHub Actions automatisch eine APK.

1. Auf dem Pixel öffnen: **Releases** im Repo → neueste Version → `Essensplan.apk` laden.
2. Installieren (beim ersten Mal „Unbekannte Apps installieren“ für den Browser erlauben).
3. Updates genauso: neue APK drüber installieren, Daten bleiben erhalten (fester Signaturschlüssel im Repo, deshalb muss das Repo privat bleiben).

## Was reinfließt

| Quelle | Weg | Wofür |
|---|---|---|
| MyBeo Waage | AFit Health → Health Connect | Gewicht, Körperfett → Grundumsatz |
| Garmin Forerunner 965 | Garmin Connect → Health Connect | aktive kcal, Schritte, Schlaf, Ruhepuls, Einheiten |
| Google Kalender | direkt am Pixel | Sporttermine erhöhen kcal und Protein, andere Termine beeinflussen die Mahlzeiten |
| YAZIO | inoffizielle App-Schnittstelle | deine Rezepte werden bevorzugt |
| Claude, sonst Gemini | API | plant die Mahlzeiten; fällt Claude aus, übernimmt Gemini |
| Prospektangebote | marktguru (inoffiziell), nach PLZ | welcher Markt diese Woche am meisten spart |
| Bring! | inoffizielle App-Schnittstelle | fertige Liste auf deiner Standardliste |

## Das Vorratsgedächtnis

* **Grundvorrat** (Salz, Zucker, Mehl, Öle, Gewürze, Brühe …) gilt als da. Ist etwas leer: im Vorrat den Haken entfernen, dann kommt es auf die nächste Liste.
* **Einkauf erledigt** bucht die letzte Liste in den Vorrat.
* **Verbrauch**: die Zutaten der geplanten Tage werden nach Ablauf des Tages automatisch abgezogen.
* **Puffer**: nachgekauft wird erst, wenn der Bestand den Bedarf plus 20 % nicht deckt.
* **Immer da**: Volvic 1,5 L, grüner Tee, schwarzer Tee, Energy Drinks mit Mindestbestand. Fällt der Bestand darunter, kommen sie auf die Liste. Mengen im Vorrat mit + und − anpassen.
* Wenig Lagerplatz ist hinterlegt: kleine Packungen, Zutaten werden über mehrere Gerichte geteilt.

## Einrichtung in der App

1. Health Connect: AFit Health und Garmin Connect müssen dort Daten **schreiben** dürfen. In der App dann „Health Connect freigeben“ und „Kalender freigeben“.
2. Claude-Key (console.anthropic.com) und Gemini-Key (aistudio.google.com, privates Konto).
3. YAZIO- und Bring-Login (YAZIO nur mit E-Mail und Passwort, nicht über Google/Apple).
4. PLZ und Märkte in deiner Nähe.
5. Widget „Essensplan“ auf den Homescreen ziehen.

Alles wird verschlüsselt nur auf dem Handy gespeichert.
