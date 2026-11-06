package cc.maue.essensplan.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * Selbst-Update wie bei Geldfluss im Blick:
 * mau-e.com/data/essensplan.json nennt die neueste Version und die APK-Adresse.
 */
object Updater {
    private const val FEED = "https://mau-e.com/data/essensplan.json"

    data class Info(val version: String, val code: Int, val url: String)

    fun check(context: Context): Info? = runCatching {
        val txt = Http.client.newCall(Request.Builder().url(FEED).header("Cache-Control", "no-cache").build())
            .execute().use { it.body?.string().orEmpty() }
        val o = JSONObject(txt)
        val info = Info(o.getString("version"), o.getInt("code"), o.getString("url"))
        val mine = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        info.takeIf { it.code > mine }
    }.getOrNull()

    fun install(context: Context, info: Info) {
        val file = File(context.cacheDir, "update.apk")
        Http.client.newCall(Request.Builder().url(info.url).build()).execute().use { r ->
            file.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
