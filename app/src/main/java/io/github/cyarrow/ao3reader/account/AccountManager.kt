package io.github.cyarrow.ao3reader.account

import io.github.cyarrow.ao3reader.ao3.Ao3Client
import io.github.cyarrow.ao3reader.ao3.Ao3Exception
import io.github.cyarrow.ao3reader.ao3.Ao3Parser
import io.github.cyarrow.ao3reader.ao3.Forms
import io.github.cyarrow.ao3reader.ao3.SearchPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** AO3 account features, implemented by submitting AO3's own web forms with the saved session cookie. */
class AccountManager(
    private val ao3: Ao3Client,
    private val cookies: PersistentCookieJar,
    private val userStore: SecureStore,
) {
    private val _user = MutableStateFlow(userStore.read())
    val user: StateFlow<String?> = _user.asStateFlow()

    sealed interface LoginResult {
        data class Success(val username: String) : LoginResult
        data class Failed(val message: String?) : LoginResult
    }

    enum class ListKind { History, MarkedForLater, Bookmarks }

    suspend fun login(username: String, password: String): LoginResult {
        val page = ao3.fetch("${Ao3Client.BASE}/users/login")
        Forms.loggedInUser(page.html)?.let { setUser(it); return LoginResult.Success(it) }
        val form = Forms.find(page.html, "form#new_user, form#loginform, form[action$=/users/login]")
            ?: return LoginResult.Failed(null)
        val result = ao3.post(
            form.action.ifEmpty { "${Ao3Client.BASE}/users/login" },
            form.with(mapOf("user[login]" to username.trim(), "user[password]" to password, "user[remember_me]" to "1")),
            referer = page.url,
        )
        val user = Forms.loggedInUser(result.html)
        return if (user != null) {
            setUser(user)
            LoginResult.Success(user)
        } else {
            LoginResult.Failed(Forms.flash(result.html))
        }
    }

    suspend fun logout() {
        runCatching {
            val page = ao3.fetch("${Ao3Client.BASE}/")
            Forms.find(page.html, "form[action$=/users/logout]")?.let { ao3.post(it.action, it.fields, page.url) }
        }
        cookies.clear()
        setUser(null)
    }

    /** Re-checks the session; clears the local user if AO3 no longer recognizes it. */
    suspend fun verify(): String? {
        if (_user.value == null) return null
        val page = runCatching { ao3.fetch("${Ao3Client.BASE}/") }.getOrNull() ?: return _user.value
        val current = Forms.loggedInUser(page.html)
        if (current == null) { cookies.clear(); setUser(null) }
        return current
    }

    suspend fun kudos(workId: Long): String? {
        val url = "${Ao3Client.BASE}/works/$workId?view_adult=true"
        val page = ao3.fetch(url)
        val form = Forms.find(page.html, "form#new_kudo, form[action$=/kudos]") ?: throw Ao3Exception.Parse("No kudos form")
        val result = ao3.post(form.action, form.fields, referer = page.url)
        return Forms.flash(result.html)
    }

    suspend fun markForLater(workId: Long) {
        requireLogin()
        ao3.fetch("${Ao3Client.BASE}/works/$workId/mark_for_later")
    }

    suspend fun bookmark(workId: Long, notes: String, private: Boolean): String? {
        requireLogin()
        val page = ao3.fetch("${Ao3Client.BASE}/works/$workId/bookmarks/new")
        val form = Forms.find(page.html, "form#bookmark-form, form[action$=/works/$workId/bookmarks], form#new_bookmark")
            ?: throw Ao3Exception.Parse("No bookmark form")
        val result = ao3.post(
            form.action,
            form.with(mapOf("bookmark[bookmarker_notes]" to notes, "bookmark[private]" to if (private) "1" else "0")),
            referer = page.url,
        )
        return Forms.flash(result.html)
    }

    suspend fun list(kind: ListKind, page: Int): SearchPage {
        val u = requireLogin()
        val url = when (kind) {
            ListKind.History -> "${Ao3Client.BASE}/users/$u/readings?page=$page"
            ListKind.MarkedForLater -> "${Ao3Client.BASE}/users/$u/readings?show=to-read&page=$page"
            ListKind.Bookmarks -> "${Ao3Client.BASE}/users/$u/bookmarks?page=$page"
        }
        val result = ao3.fetch(url)
        if (result.url.contains("/users/login")) { setUser(null); throw Ao3Exception.NeedsLogin() }
        return Ao3Parser.parseWorkList(result.html, page)
    }

    private fun requireLogin(): String = _user.value ?: throw Ao3Exception.NeedsLogin()

    private fun setUser(name: String?) {
        _user.value = name
        userStore.write(name)
    }
}
