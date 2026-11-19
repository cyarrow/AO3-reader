package io.github.cyarrow.ao3reader.ao3

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
data class SearchQuery(
    val query: String = "",
    val title: String = "",
    val creators: String = "",
    val fandoms: String = "",
    val relationships: String = "",
    val characters: String = "",
    val freeforms: String = "",
    /** Tags to exclude; turned into negative terms in the query string. */
    val excludedTags: String = "",
    val language: String = "zh",
    val ratings: Set<Rating> = emptySet(),
    val completion: Completion = Completion.Any,
    val crossover: Crossover = Crossover.Include,
    val words: String = "",
    val sort: Sort = Sort.BestMatch,
    val ascending: Boolean = false,
) {
    enum class Rating(val ao3Id: Int) { NotRated(9), General(10), Teen(11), Mature(12), Explicit(13) }
    enum class Completion(val value: String) { Any(""), Complete("T"), InProgress("F") }
    enum class Crossover(val value: String) { Include(""), Exclude("F"), Only("T") }
    enum class Sort(val column: String) {
        BestMatch("_score"), Updated("revised_at"), Posted("created_at"), Kudos("kudos_count"),
        Hits("hits"), Bookmarks("bookmarks_count"), Comments("comments_count"), Words("word_count"),
        Title("title_to_sort_on"), Author("authors_to_sort_on"),
    }

    /** True when nothing narrows the search beyond language and sort. */
    val isBrowse: Boolean get() = query.isBlank() && title.isBlank() && creators.isBlank() && fandoms.isBlank() &&
        relationships.isBlank() && characters.isBlank() && freeforms.isBlank()

    /**
     * [keywordVariants] are other-script spellings of [query]; any of them may match, so a work written
     * in Simplified Chinese is found by a Traditional query and vice versa.
     */
    fun toUrl(page: Int, keywordVariants: List<String> = emptyList()): HttpUrl {
        val keyword = query.trim()
        val alternates = keywordVariants.map { it.trim() }.filter { it.isNotEmpty() && it != keyword }.distinct()
        val fullQuery = buildString {
            if (alternates.isEmpty() || keyword.isEmpty()) append(keyword)
            else append((listOf(keyword) + alternates).joinToString(" OR ") { "($it)" })
            excludedTags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                append(" -\"").append(it.replace("\"", "")).append('"')
            }
        }.trim()
        val b = "${Ao3Client.BASE}/works/search".toHttpUrl().newBuilder()
            .addQueryParameter("commit", "Search")
            .addQueryParameter("page", page.toString())
        fun p(name: String, value: String) = b.addQueryParameter("work_search[$name]", value)
        p("query", fullQuery)
        p("title", title.trim())
        p("creators", creators.trim())
        p("fandom_names", fandoms.trim())
        p("relationship_names", relationships.trim())
        p("character_names", characters.trim())
        p("freeform_names", freeforms.trim())
        p("language_id", language)
        p("complete", completion.value)
        p("crossover", crossover.value)
        p("word_count", words.trim())
        p("sort_column", sort.column)
        p("sort_direction", if (ascending) "asc" else "desc")
        ratings.sortedBy { it.ao3Id }.forEach { b.addQueryParameter("work_search[rating_ids][]", it.ao3Id.toString()) }
        return b.build()
    }
}
