package io.github.cyarrow.ao3reader.ao3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {
    @Test fun combinesScriptVariants() {
        val url = SearchQuery(query = "結構").toUrl(1, listOf("結構", "结构"))
        assertEquals("(結構) OR (结构)", url.queryParameter("work_search[query]"))
    }

    @Test fun plainQueryUnchanged() {
        assertEquals("harry", SearchQuery(query = "harry").toUrl(1).queryParameter("work_search[query]"))
        assertEquals("zh", SearchQuery().toUrl(1).queryParameter("work_search[language_id]"))
    }

    @Test fun excludedTagsAppended() {
        val url = SearchQuery(query = "a", excludedTags = "x, y").toUrl(2)
        assertEquals("a -\"x\" -\"y\"", url.queryParameter("work_search[query]"))
        assertEquals("2", url.queryParameter("page"))
    }

    @Test fun browseDetection() {
        assertTrue(SearchQuery().isBrowse)
        assertFalse(SearchQuery(fandoms = "F").isBrowse)
    }
}

class Ao3HostTest {
    @Test fun onlyAo3OverHttps() {
        assertTrue(isAo3("https://archiveofourown.org/users/login"))
        assertTrue(isAo3("https://www.archiveofourown.org/kudos"))
        assertFalse(isAo3("http://archiveofourown.org/users/login"))
        assertFalse(isAo3("https://archiveofourown.org.evil.example/users/login"))
        assertFalse(isAo3("https://evil.example/archiveofourown.org"))
        assertFalse(isAo3(""))
    }
}
