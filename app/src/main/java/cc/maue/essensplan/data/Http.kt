package cc.maue.essensplan.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS) // KI-Antworten können dauern
        .build()
}

class HttpException(val code: Int, body: String) : Exception("HTTP $code: ${body.take(300)}")
