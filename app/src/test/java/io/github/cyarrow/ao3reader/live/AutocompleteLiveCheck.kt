package io.github.cyarrow.ao3reader.live

import io.github.cyarrow.ao3reader.ao3.Ao3Client
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Manual one-off check against AO3; only runs with -Dlive=true. */
class AutocompleteLiveCheck {
    @Test fun fandom() {
        assumeTrue(System.getProperty("live") == "true")
        val client = OkHttpClient.Builder().addInterceptor { c ->
            c.proceed(c.request().newBuilder().header("User-Agent", "AO3Reader/0.3.2 (Android)").build())
        }.build()
        val result = runCatching { runBlocking { Ao3Client(client).autocomplete("fandom", "RIIZE") } }
        println("RESULT: $result")
        result.exceptionOrNull()?.printStackTrace(System.out)
    }
}
