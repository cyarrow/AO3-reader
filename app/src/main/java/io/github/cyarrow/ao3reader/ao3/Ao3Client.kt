package io.github.cyarrow.ao3reader.ao3

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Minimal AO3 client. AO3 has no public API, so pages are fetched and parsed.
 * Requests are serialized and spaced out to stay well within AO3's rate limits.
 */
class Ao3Client(private val client: OkHttpClient) {
    companion object {
        const val BASE = "https://archiveofourown.org"
        private const val MIN_INTERVAL_MS = 1500L
    }

    private val mutex = Mutex()
    private var lastRequestAt = 0L

    suspend fun search(query: SearchQuery, page: Int): SearchPage {
        val html = get(query.toUrl(page))
        return Ao3Parser.parseWorkList(html, page)
    }

    /** Fetches a complete work (all chapters) through AO3's HTML download. */
    suspend fun fetchWork(id: Long): Work {
        val workPage = get("$BASE/works/$id?view_adult=true".toHttpUrl())
        val downloadUrl = Ao3Parser.parseDownloadUrl(workPage)
        val html = get(downloadUrl.toHttpUrl())
        return Ao3Parser.parseDownload(html, id).copy(fetchedAt = System.currentTimeMillis())
    }

    private suspend fun get(url: HttpUrl): String = mutex.withLock {
        val wait = lastRequestAt + MIN_INTERVAL_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try {
            withContext(Dispatchers.IO) {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    when {
                        resp.code == 429 -> throw Ao3Exception.RateLimited(resp.header("Retry-After")?.toLongOrNull())
                        resp.code == 404 -> throw Ao3Exception.NotFound()
                        resp.request.url.encodedPath.startsWith("/users/login") -> throw Ao3Exception.NeedsLogin()
                        !resp.isSuccessful -> throw Ao3Exception.Unavailable(resp.code)
                    }
                    resp.body.string()
                }
            }
        } finally {
            lastRequestAt = System.currentTimeMillis()
        }
    }
}

/** Extracts a work id from a pasted AO3 link or a bare number. */
fun parseWorkId(input: String): Long? {
    val trimmed = input.trim()
    trimmed.toLongOrNull()?.let { return it }
    return Regex("""/works/(\d+)""").find(trimmed)?.groupValues?.get(1)?.toLongOrNull()
}
