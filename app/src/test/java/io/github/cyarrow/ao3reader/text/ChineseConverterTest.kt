package io.github.cyarrow.ao3reader.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChineseConverterTest {
    private val converter by lazy {
        ChineseConverter.fromStreams { File("src/main/assets/opencc/$it").inputStream() }
    }

    @Test fun convertsCharactersAndPhrases() {
        assertEquals("頭髮很長，他說話了。", converter.toTraditional("头发很长，他说话了。", taiwanPhrases = false))
        assertEquals("後來", converter.toTraditional("后来", taiwanPhrases = false))
    }

    @Test fun appliesTaiwanPhrasesWhenRequested() {
        assertEquals("軟體", converter.toTraditional("软件", taiwanPhrases = true))
    }

    @Test fun detectsScript() {
        assertTrue(converter.looksSimplified("他们说这里的风景很美，我们应该一起去看看。"))
        assertFalse(converter.looksSimplified("他們說這裡的風景很美，我們應該一起去看看。"))
        assertFalse(converter.looksSimplified("Hello world"))
    }
}
