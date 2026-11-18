package io.github.cyarrow.ao3reader.reader

import io.github.cyarrow.ao3reader.data.LibraryEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryEntryTest {
    private fun entry(saved: String, remote: String) = LibraryEntry(1, "t", emptyList(), 5, savedChapters = saved, remoteChapters = remote)

    @Test fun detectsNewChapters() {
        assertTrue(entry("5/?", "6/?").hasUpdate)
        assertFalse(entry("5/?", "5/?").hasUpdate)
        assertFalse(entry("5/5", "").hasUpdate)
        assertTrue(entry("5/10", "7/10").hasUpdate)
    }
}
