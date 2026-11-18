package io.github.cyarrow.ao3reader.ao3

import kotlinx.serialization.Serializable

@Serializable
data class WorkSummary(
    val id: Long,
    val title: String,
    val authors: List<String>,
    val fandoms: List<String> = emptyList(),
    val rating: String = "",
    val warnings: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val complete: Boolean = false,
    val relationships: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val freeforms: List<String> = emptyList(),
    val summary: String = "",
    val language: String = "",
    val words: Int = 0,
    val chapters: String = "",
    val kudos: Int = 0,
    val comments: Int = 0,
    val bookmarks: Int = 0,
    val hits: Int = 0,
    val updated: String = "",
    val restricted: Boolean = false,
)

data class SearchPage(
    val works: List<WorkSummary>,
    val page: Int,
    val hasNext: Boolean,
    val totalText: String?,
)

/** One unit of reading content. Inline styles are kept as character ranges so text can be re-laid out freely. */
@Serializable
data class Block(
    val kind: Kind,
    val text: String = "",
    val spans: List<StyleSpan> = emptyList(),
    val center: Boolean = false,
) {
    @Serializable
    enum class Kind { Paragraph, Heading, Divider, Quote }
}

@Serializable
data class StyleSpan(val start: Int, val end: Int, val style: Style) {
    @Serializable
    enum class Style { Bold, Italic, Underline, Strike }
}

@Serializable
data class Chapter(
    val index: Int,
    val title: String,
    val summary: List<Block> = emptyList(),
    val notes: List<Block> = emptyList(),
    val endNotes: List<Block> = emptyList(),
    val body: List<Block>,
)

@Serializable
data class Work(
    val id: Long,
    val title: String,
    val authors: List<String>,
    val fandoms: List<String> = emptyList(),
    val rating: String = "",
    val warnings: List<String> = emptyList(),
    val relationships: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val freeforms: List<String> = emptyList(),
    val language: String = "",
    val words: Int = 0,
    val chaptersText: String = "",
    val kudos: Int = 0,
    val published: String = "",
    val updated: String = "",
    val summary: List<Block> = emptyList(),
    val notes: List<Block> = emptyList(),
    val endNotes: List<Block> = emptyList(),
    val chapters: List<Chapter>,
    val fetchedAt: Long = 0,
)

sealed class Ao3Exception(message: String) : Exception(message) {
    class RateLimited(val retryAfterSeconds: Long?) : Ao3Exception("Rate limited")
    class NotFound : Ao3Exception("Not found")
    class NeedsLogin : Ao3Exception("Login required")
    class Unavailable(code: Int) : Ao3Exception("AO3 unavailable ($code)")
    class Parse(message: String) : Ao3Exception(message)
}
