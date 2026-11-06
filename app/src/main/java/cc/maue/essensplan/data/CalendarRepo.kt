package cc.maue.essensplan.data

import android.content.Context
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Liest Termine aus dem Google Kalender am Pixel (keine Cloud nötig). */
class CalendarRepo(private val context: Context) {

    data class Event(val day: LocalDate, val title: String, val minutes: Long, val allDay: Boolean, val isSport: Boolean)

    fun events(from: LocalDate, to: LocalDate, sportKeywords: List<String>): List<Event> {
        val zone = ZoneId.systemDefault()
        val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, start)
            android.content.ContentUris.appendId(it, end)
        }.build()

        val cols = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        val out = mutableListOf<Event>()
        context.contentResolver.query(uri, cols, null, null, CalendarContract.Instances.BEGIN)?.use { c ->
            while (c.moveToNext()) {
                val title = c.getString(0) ?: continue
                val b = c.getLong(1); val e = c.getLong(2)
                val lower = title.lowercase()
                out += Event(
                    day = Instant.ofEpochMilli(b).atZone(zone).toLocalDate(),
                    title = title,
                    minutes = (e - b) / 60000,
                    allDay = c.getInt(3) == 1,
                    isSport = sportKeywords.any { it.isNotBlank() && lower.contains(it.trim().lowercase()) },
                )
            }
        }
        return out
    }
}
