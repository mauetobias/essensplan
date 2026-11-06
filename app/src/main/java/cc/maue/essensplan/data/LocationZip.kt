package cc.maue.essensplan.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import java.util.Locale

/** PLZ aus dem aktuellen Handystandort (grob reicht). Fällt zurück auf die zuletzt bekannte PLZ. */
object LocationZip {
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun current(context: Context): String? = runCatching {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time } ?: return null
        Geocoder(context, Locale.GERMANY).getFromLocation(loc.latitude, loc.longitude, 1)
            ?.firstOrNull()?.postalCode
    }.getOrNull()
}
