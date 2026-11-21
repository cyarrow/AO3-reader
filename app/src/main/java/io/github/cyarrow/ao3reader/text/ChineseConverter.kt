package io.github.cyarrow.ao3reader.text

import java.io.InputStream

/**
 * Offline Simplified → Traditional Chinese conversion using OpenCC dictionaries
 * (forward maximum matching, the same strategy OpenCC uses).
 */
class ChineseConverter private constructor(
    private val s2t: Dict,
    private val tw: Dict,
    private val t2s: Dict,
    private val simplifiedOnly: Set<Char>,
    private val traditionalOnly: Set<Char>,
) {
    /** A phrase dictionary keyed by string, with the longest key length for bounded lookups. */
    class Dict(val map: Map<String, String>) {
        val maxLen: Int = map.keys.maxOfOrNull { it.length } ?: 1

        fun convert(text: String): String {
            if (map.isEmpty()) return text
            val out = StringBuilder(text.length)
            var i = 0
            while (i < text.length) {
                var matched = false
                var len = minOf(maxLen, text.length - i)
                while (len > 0) {
                    val hit = map[text.substring(i, i + len)]
                    if (hit != null) {
                        out.append(hit)
                        i += len
                        matched = true
                        break
                    }
                    len--
                }
                if (!matched) {
                    out.append(text[i])
                    i++
                }
            }
            return out.toString()
        }
    }

    fun toSimplified(text: String): String = t2s.convert(text)

    /** The other-script spellings of [text] (e.g. 結構 → 结构), for searching works written in either script. */
    fun variants(text: String): List<String> =
        listOf(text, toSimplified(text), toTraditional(text, taiwanPhrases = false)).distinct()

    fun toTraditional(text: String, taiwanPhrases: Boolean): String {
        val t = s2t.convert(text)
        return if (taiwanPhrases) tw.convert(t) else t
    }

    /**
     * Guesses whether [sample] is Simplified Chinese by counting characters that
     * only occur in one script.
     */
    fun looksSimplified(sample: String): Boolean {
        var simp = 0
        var trad = 0
        for (c in sample) {
            if (c in simplifiedOnly) simp++ else if (c in traditionalOnly) trad++
        }
        return simp >= 5 && simp > trad * 2
    }

    companion object {
        fun parseDict(input: InputStream): Map<String, String> {
            val map = HashMap<String, String>()
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (line.isEmpty() || line.startsWith("#")) continue
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    val key = line.substring(0, tab)
                    val value = line.substring(tab + 1).substringBefore(' ').trim()
                    if (value.isNotEmpty()) map[key] = value
                }
            }
            return map
        }

        fun create(
            stCharacters: Map<String, String>,
            stPhrases: Map<String, String>,
            twPhrases: Map<String, String>,
            twVariants: Map<String, String>,
            tsCharacters: Map<String, String> = emptyMap(),
            tsPhrases: Map<String, String> = emptyMap(),
        ): ChineseConverter {
            val s2t = HashMap<String, String>(stCharacters.size + stPhrases.size)
            s2t.putAll(stCharacters)
            s2t.putAll(stPhrases)
            val tw = HashMap<String, String>(twPhrases.size + twVariants.size)
            tw.putAll(twVariants)
            tw.putAll(twPhrases)

            // Characters that change under s2t are simplified-specific; their targets are traditional-specific.
            val simplifiedOnly = HashSet<Char>()
            val traditionalOnly = HashSet<Char>()
            for ((k, v) in stCharacters) {
                if (k.length == 1 && v.length == 1 && k != v) {
                    simplifiedOnly += k[0]
                    traditionalOnly += v[0]
                }
            }
            // A character that is a valid output as well (e.g. 后 / 後 ambiguity) is not a reliable signal.
            val ambiguous = simplifiedOnly intersect traditionalOnly
            simplifiedOnly -= ambiguous
            traditionalOnly -= ambiguous
            val t2s = HashMap<String, String>(tsCharacters.size + tsPhrases.size)
            t2s.putAll(tsCharacters)
            t2s.putAll(tsPhrases)
            return ChineseConverter(Dict(s2t), Dict(tw), Dict(t2s), simplifiedOnly, traditionalOnly)
        }

        fun fromStreams(open: (String) -> InputStream): ChineseConverter = create(
            stCharacters = open("STCharacters.txt").use(::parseDict),
            stPhrases = open("STPhrases.txt").use(::parseDict),
            twPhrases = open("TWPhrases.txt").use(::parseDict),
            twVariants = open("TWVariants.txt").use(::parseDict),
            tsCharacters = open("TSCharacters.txt").use(::parseDict),
            tsPhrases = open("TSPhrases.txt").use(::parseDict),
        )
    }
}
