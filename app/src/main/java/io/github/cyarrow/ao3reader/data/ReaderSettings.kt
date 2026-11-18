package io.github.cyarrow.ao3reader.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.cyarrow.ao3reader.ao3.SearchQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ReadingMode { Scroll, Paged }

@Serializable
enum class ReaderTheme { Light, Paper, Green, Dark, Black }

@Serializable
data class ReaderSettings(
    val mode: ReadingMode = ReadingMode.Scroll,
    val fontId: String = "noto_serif_tc",
    val fontSize: Float = 19f,
    val fontWeight: Int = 400,
    val lineHeight: Float = 1.8f,
    /** Space between paragraphs, in multiples of the font size. */
    val paragraphSpacing: Float = 0.9f,
    val horizontalMargin: Int = 22,
    val indent: Boolean = true,
    val justify: Boolean = true,
    val theme: ReaderTheme = ReaderTheme.Paper,
    val convertMode: ConvertMode = ConvertMode.Auto,
    val taiwanPhrases: Boolean = false,
    val keepScreenOn: Boolean = true,
    val volumeKeys: Boolean = true,
    val tapToTurn: Boolean = true,
    val showNotes: Boolean = true,
    /** Screen brightness 0..1, or negative to follow the system. */
    val brightness: Float = -1f,
    val tapHintShown: Boolean = false,
)

private val Context.readerStore by preferencesDataStore(name = "reader")

/** Reader settings and the last search, stored as JSON in DataStore. */
class ReaderPreferences(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val settingsKey = stringPreferencesKey("reader_settings")
    private val searchKey = stringPreferencesKey("last_search")

    val settings: Flow<ReaderSettings> = context.readerStore.data.map { prefs ->
        prefs[settingsKey]?.let { runCatching { json.decodeFromString<ReaderSettings>(it) }.getOrNull() }
            ?: ReaderSettings()
    }

    suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        context.readerStore.edit { prefs ->
            val current = prefs[settingsKey]?.let { runCatching { json.decodeFromString<ReaderSettings>(it) }.getOrNull() }
                ?: ReaderSettings()
            prefs[settingsKey] = json.encodeToString(ReaderSettings.serializer(), transform(current))
        }
    }

    val lastSearch: Flow<SearchQuery> = context.readerStore.data.map { prefs ->
        prefs[searchKey]?.let { runCatching { json.decodeFromString<SearchQuery>(it) }.getOrNull() } ?: SearchQuery()
    }

    suspend fun saveSearch(query: SearchQuery) {
        context.readerStore.edit { it[searchKey] = json.encodeToString(SearchQuery.serializer(), query) }
    }
}
