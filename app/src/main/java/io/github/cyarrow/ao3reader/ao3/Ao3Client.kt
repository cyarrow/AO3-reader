package io.github.cyarrow.ao3reader.ao3

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Minimal AO3 client. AO3 has no public API, so pages are fetched and parsed.
 * Requests are serialized and spaced out to stay well within AO3's rate limits.
 */
class Ao3Client(private val client: OkHttpClient) {
    companion object {
        const val BASE = "https://archiveofourown.org"
        /** Spacing between requests: polite to AO3 without making the reader wait. */
        private const val MIN_INTERVAL_MS = 400L
    }

    private val mutex = Mutex()
    private var lastRequestAt = 0L

    suspend fun search(query: SearchQuery, page: Int, keywordVariants: List<String> = emptyList()): SearchPage {
        val html = get(query.toUrl(page, keywordVariants))
        return Ao3Parser.parseWorkList(html, page)
    }

    /** Fetches a complete work (all chapters) through AO3's HTML download. */
    suspend fun fetchWork(id: Long, onPreview: (suspend (Work) -> Unit)? = null): Work {
        val workPage = get("$BASE/works/$id?view_adult=true".toHttpUrl())
        val downloadUrl = Ao3Parser.parseDownloadUrl(workPage)
        // The work page already contains the first chapter: hand it over while the full text downloads.
        if (onPreview != null) runCatching { Ao3Parser.parseWorkPage(workPage, id) }.getOrNull()?.let { onPreview(it) }
        val html = get(downloadUrl.toHttpUrl())
        return Ao3Parser.parseDownload(html, id).copy(fetchedAt = System.currentTimeMillis())
    }

    /** Lightweight check: current chapter count and update date, from the work page only. */
    suspend fun fetchWorkStats(id: Long): Pair<String, String> =
        Ao3Parser.parseWorkStats(get("$BASE/works/$id?view_adult=true".toHttpUrl()))

    /** AO3's tag autocomplete: type is fandom, relationship, character or freeform. */
    suspend fun autocomplete(type: String, term: String): List<String> {
        if (term.isBlank()) return emptyList()
        val url = "$BASE/autocomplete/$type".toHttpUrl().newBuilder().addQueryParameter("term", term.trim()).build()
        return Ao3Parser.parseAutocomplete(getWithRetry(url))
    }

    /** AO3's tag search: substring and Chinese matches the autocomplete can miss, most-used first. */
    suspend fun searchTags(term: String): List<Ao3Parser.TagHit> {
        val url = "$BASE/tags/search".toHttpUrl().newBuilder()
            .addQueryParameter("tag_search[name]", term.trim())
            .addQueryParameter("tag_search[sort_column]", "uses")
            .addQueryParameter("tag_search[sort_direction]", "desc")
            .addQueryParameter("commit", "Search Tags")
            .build()
        return Ao3Parser.parseTagSearch(getWithRetry(url))
    }

    /** Light requests retry once when AO3 asks us to slow down. */
    private suspend fun getWithRetry(url: HttpUrl): String = try {
        get(url)
    } catch (e: Ao3Exception.RateLimited) {
        delay(((e.retryAfterSeconds ?: 2L).coerceIn(1L, 5L)) * 1000)
        get(url)
    }

    /** Result of a raw request: final URL after redirects and the body. */
    data class Page(val url: String, val html: String)

    /** GET without login-wall detection, for pages that may legitimately be the login form. */
    suspend fun fetch(url: String): Page = request(Request.Builder().url(url).build(), allowLogin = true)

    suspend fun post(url: String, fields: List<Pair<String, String>>, referer: String? = null): Page {
        // Credentials and session-bound forms may only ever be submitted to AO3 itself over HTTPS,
        // whatever action URL a parsed form claims.
        require(isAo3(url)) { "Refusing to submit a form outside AO3" }
        val body = okhttp3.FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        val req = Request.Builder().url(url).post(body).apply { if (referer != null) header("Referer", referer) }.build()
        return request(req, allowLogin = true)
    }

    private suspend fun get(url: HttpUrl): String = request(Request.Builder().url(url).build(), allowLogin = false).html

    private suspend fun request(request: Request, allowLogin: Boolean): Page = mutex.withLock {
        val wait = lastRequestAt + MIN_INTERVAL_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { resp ->
                    when {
                        resp.code == 429 -> throw Ao3Exception.RateLimited(resp.header("Retry-After")?.toLongOrNull())
                        resp.code == 404 -> throw Ao3Exception.NotFound()
                        !allowLogin && resp.request.url.encodedPath.startsWith("/users/login") -> throw Ao3Exception.NeedsLogin()
                        !resp.isSuccessful && resp.code != 422 -> throw Ao3Exception.Unavailable(resp.code)
                    }
                    Page(resp.request.url.toString(), resp.body.string())
                }
            }
        } finally {
            lastRequestAt = System.currentTimeMillis()
        }
    }
}

/** True for https URLs on archiveofourown.org (or its www host). */
fun isAo3(url: String): Boolean {
    val u = url.toHttpUrlOrNull() ?: return false
    return u.isHttps && (u.host == "archiveofourown.org" || u.host == "www.archiveofourown.org")
}

/** Extracts a work id from a pasted AO3 link or a bare number. */
fun parseWorkId(input: String): Long? {
    val trimmed = input.trim()
    trimmed.toLongOrNull()?.let { return it }
    return Regex("""/works/(\d+)""").find(trimmed)?.groupValues?.get(1)?.toLongOrNull()
}
