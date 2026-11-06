package cc.maue.essensplan.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Alle Schlüssel, Logins und Profilwerte, verschlüsselt auf dem Gerät. */
class Settings(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "essensplan_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private fun s(key: String, def: String = "") = prefs.getString(key, def) ?: def
    private fun put(key: String, v: String) = prefs.edit().putString(key, v).apply()

    var claudeKey get() = s("claude_key"); set(v) = put("claude_key", v)
    var claudeModel get() = s("claude_model", "claude-sonnet-5-5"); set(v) = put("claude_model", v)
    var geminiKey get() = s("gemini_key"); set(v) = put("gemini_key", v)
    var geminiModel get() = s("gemini_model", "gemini-2.5-flash"); set(v) = put("gemini_model", v)

    var bringEmail get() = s("bring_email"); set(v) = put("bring_email", v)
    var bringPassword get() = s("bring_pw"); set(v) = put("bring_pw", v)
    var yazioEmail get() = s("yazio_email"); set(v) = put("yazio_email", v)
    var yazioPassword get() = s("yazio_pw"); set(v) = put("yazio_pw", v)

    // Profil (Fallback, falls Health Connect nichts liefert)
    var birthYear get() = s("birth_year", "1996"); set(v) = put("birth_year", v)
    var heightCm get() = s("height_cm", "180"); set(v) = put("height_cm", v)
    var sex get() = s("sex", "m"); set(v) = put("sex", v)
    var goal get() = s("goal", "Gewicht halten, Muskelaufbau, viel Protein"); set(v) = put("goal", v)
    var dislikes get() = s("dislikes", "kein Kaffee"); set(v) = put("dislikes", v)
    var sportKeywords get() = s("sport_kw", "crossfit,training,gym,laufen,run,wod,sport"); set(v) = put("sport_kw", v)

    var zip get() = s("zip"); set(v) = put("zip", v)
    var stores get() = s("stores", "REWE,Edeka,Lidl,Aldi,Kaufland,Penny,Netto"); set(v) = put("stores", v)
    var storage get() = s("storage", "wenig Lagerplatz, kleine Packungen, lieber öfter einkaufen"); set(v) = put("storage", v)

    /** Letzte Einkaufsliste als JSON, damit "Einkauf erledigt" sie in den Vorrat buchen kann. */
    var lastShopping get() = s("last_shopping", "[]"); set(v) = put("last_shopping", v)

    var lastPlan get() = s("last_plan"); set(v) = put("last_plan", v)
    var lastPlanTitle get() = s("last_plan_title"); set(v) = put("last_plan_title", v)
}
