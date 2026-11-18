package io.github.cyarrow.ao3reader.ao3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormsTest {
    @Test fun detectsLoggedInUser() {
        val html = """<div id="header"><div id="greeting"><ul class="user navigation actions">
            <li class="dropdown"><a href="/users/reader123">Hi, reader123!</a>
            <ul class="menu"><li><a href="/users/reader123/pseuds/reader123">My Dashboard</a></li></ul></li>
            </ul></div></div>"""
        assertEquals("reader123", Forms.loggedInUser(html))
        assertNull(Forms.loggedInUser("""<div id="login"><a id="login-dropdown" href="/users/login">Log In</a></div>"""))
    }

    @Test fun parsesLoginForm() {
        val html = """<form class="new_user" id="new_user" action="/users/login" method="post">
            <input type="hidden" name="authenticity_token" value="TOKEN" />
            <input type="text" name="user[login]" id="user_login" />
            <input type="password" name="user[password]" />
            <input name="user[remember_me]" type="hidden" value="0" />
            <input type="checkbox" value="1" name="user[remember_me]" />
            <input type="submit" name="commit" value="Log In" />
        </form>"""
        val form = Forms.find(html, "form#new_user")!!
        assertEquals("https://archiveofourown.org/users/login", form.action)
        val fields = form.with(mapOf("user[login]" to "me", "user[password]" to "pw", "user[remember_me]" to "1"))
        assertEquals("TOKEN", fields.first { it.first == "authenticity_token" }.second)
        assertEquals(listOf("1"), fields.filter { it.first == "user[remember_me]" }.map { it.second })
        assertEquals("me", fields.first { it.first == "user[login]" }.second)
    }

    @Test fun readsFlashError() {
        assertEquals("The password or user name you entered doesn't match our records.",
            Forms.flash("""<div class="flash error">The password or user name you entered doesn't match our records.</div>"""))
    }
}
