package io.github.cyarrow.ao3reader.account

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

@Serializable
private data class StoredCookie(
    val name: String, val value: String, val expiresAt: Long, val domain: String, val path: String,
    val secure: Boolean, val httpOnly: Boolean, val hostOnly: Boolean, val persistent: Boolean,
)

/** Keeps AO3 session cookies across launches, encrypted at rest. */
class PersistentCookieJar(private val store: SecureStore?) : CookieJar {
    private val json = Json { ignoreUnknownKeys = true }
    private val cookies = mutableListOf<Cookie>()

    init {
        store?.read()?.let { text ->
            runCatching { json.decodeFromString<List<StoredCookie>>(text) }.getOrNull()?.forEach { c ->
                val b = Cookie.Builder().name(c.name).value(c.value).path(c.path)
                if (c.hostOnly) b.hostOnlyDomain(c.domain) else b.domain(c.domain)
                if (c.persistent) b.expiresAt(c.expiresAt)
                if (c.secure) b.secure()
                if (c.httpOnly) b.httpOnly()
                cookies += b.build()
            }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (c in cookies) {
            this.cookies.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
            if (c.expiresAt > System.currentTimeMillis()) this.cookies += c
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt <= now }
        return cookies.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        persist()
    }

    private fun persist() = runCatching {
        store?.write(json.encodeToString(cookies.filter { it.persistent || it.name.contains("session") }.map {
            StoredCookie(it.name, it.value, it.expiresAt, it.domain, it.path, it.secure, it.httpOnly, it.hostOnly, it.persistent)
        }))
    }.let { }
}
