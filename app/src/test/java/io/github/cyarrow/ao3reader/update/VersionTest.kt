package io.github.cyarrow.ao3reader.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test fun parsesTagsAndSuffixes() {
        assertEquals(Version(1, 2, 3), Version.parse("v1.2.3"))
        assertEquals(Version(0, 1, 0), Version.parse("0.1.0-debug"))
        assertEquals(Version(2, 0, 0), Version.parse("2"))
        assertNull(Version.parse("abc"))
        assertNull(Version.parse("1.2.3.4"))
    }

    @Test fun comparesNumerically() {
        assertTrue(Version.parse("0.10.0")!! > Version.parse("0.9.9")!!)
        assertTrue(Version.parse("1.0.0")!! > Version.parse("0.99.99")!!)
    }
}
