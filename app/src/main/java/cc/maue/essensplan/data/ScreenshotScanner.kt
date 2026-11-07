package cc.maue.essensplan.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.provider.MediaStore
import android.util.Base64
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream

/**
 * Durchsucht die Screenshots auf dem Handy nach YAZIO-Rezepten.
 * 1. Texterkennung direkt im Handy (ML Kit, offline): nur Bilder mit YAZIO- oder Rezept-Text kommen weiter.
 * 2. Screenshots, die kurz nacheinander entstanden sind, gehören zu einem Rezept.
 * 3. Gemini liest jede Gruppe und legt das Rezept im Rezeptbuch ab.
 * Schon geprüfte Bilder werden gemerkt und beim nächsten Mal übersprungen.
 */
class ScreenshotScanner(private val context: Context) {

    private val prefs = context.getSharedPreferences("screenshot_scan", Context.MODE_PRIVATE)

    data class Shot(val id: Long, val takenMs: Long)

    fun run(progress: (String) -> Unit): String {
        val seen = prefs.getStringSet("seen", emptySet())!!.toMutableSet()
        val shots = screenshots().filter { it.id.toString() !in seen }
        if (shots.isEmpty()) return "Keine neuen Screenshots gefunden."

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val hits = mutableListOf<Shot>()
        shots.forEachIndexed { i, s ->
            if (i % 10 == 0) progress("Prüfe Screenshot ${i + 1} von ${shots.size} …")
            runCatching {
                val text = Tasks.await(recognizer.process(InputImage.fromBitmap(load(s.id, 1200), 0))).text.lowercase()
                val recipe = "yazio" in text || ("zutaten" in text && ("portion" in text || "kcal" in text)) ||
                    ("zubereitung" in text && ("min" in text || "schritt" in text))
                if (recipe) hits += s
            }
            seen += s.id.toString()
        }
        recognizer.close()

        // Kurz nacheinander gemachte Screenshots (max. 3 Minuten Abstand) zu einem Rezept zusammenfassen
        val groups = mutableListOf<MutableList<Shot>>()
        hits.sortedBy { it.takenMs }.forEach { s ->
            val g = groups.lastOrNull()
            if (g != null && s.takenMs - g.last().takenMs < 3 * 60_000 && g.size < 6) g += s else groups += mutableListOf(s)
        }

        val llm = LlmClient(Settings(context))
        val book = RecipeBook(context)
        val names = mutableListOf<String>()
        groups.forEachIndexed { i, g ->
            progress("Lese Rezept ${i + 1} von ${groups.size} …")
            runCatching {
                val imgs = g.map { Base64.encodeToString(jpeg(load(it.id, 1600)), Base64.NO_WRAP) to "image/jpeg" }
                llm.extractRecipeFromImages(imgs)?.let { names += book.addFromJson(it) }
            }
        }
        prefs.edit().putStringSet("seen", seen).apply()
        return if (names.isEmpty()) "${shots.size} Screenshots geprüft, keine neuen Rezepte gefunden."
        else "${names.size} Rezepte übernommen: " + names.joinToString(", ")
    }

    /** Alle Bilder aus Screenshot-Ordnern, älteste zuerst. */
    private fun screenshots(): List<Shot> {
        val out = mutableListOf<Shot>()
        val cols = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols,
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} LIKE ?",
            arrayOf("%Screenshot%", "%Screenshot%"), "${MediaStore.Images.Media.DATE_ADDED} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val taken = c.getLong(1).takeIf { it > 0 } ?: (c.getLong(2) * 1000)
                out += Shot(c.getLong(0), taken)
            }
        }
        return out
    }

    private fun load(id: Long, maxSide: Int): Bitmap {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { dec, info, _ ->
            val big = maxOf(info.size.width, info.size.height)
            if (big > maxSide) dec.setTargetSize(info.size.width * maxSide / big, info.size.height * maxSide / big)
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private fun jpeg(b: Bitmap) = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
}
