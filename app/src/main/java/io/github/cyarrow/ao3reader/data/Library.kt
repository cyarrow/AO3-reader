package io.github.cyarrow.ao3reader.data

import android.content.Context
import io.github.cyarrow.ao3reader.ao3.Work
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Simplified → Traditional conversion preference. */
@Serializable
enum class ConvertMode { Auto, On, Off }

@Serializable
data class LibraryEntry(
    val workId: Long,
    val title: String,
    val authors: List<String>,
    val chapterCount: Int,
    val fandoms: List<String> = emptyList(),
    val relationships: List<String> = emptyList(),
    val chapterIndex: Int = 0,
    /** Index into the chapter's display blocks, and character offset inside that block. */
    val blockIndex: Int = 0,
    val charOffset: Int = 0,
    /** Overall progress 0..1, for display only. */
    val fraction: Float = 0f,
    val lastReadAt: Long = 0,
    val convert: ConvertMode? = null,
    /** Chapters text of the saved copy, e.g. "22/?". */
    val savedChapters: String = "",
    val savedUpdated: String = "",
    /** Chapters text AO3 reported at the last update check. */
    val remoteChapters: String = "",
    val checkedAt: Long = 0,
    val addedAt: Long = 0,
    /** Marked as finished by the reader ("作品讀完了"); stays until reading again from the start. */
    val finished: Boolean = false,
) {
    val isFinished: Boolean get() = finished || fraction >= 0.999f

    /** Published chapter count is the number before the slash. */
    val hasUpdate: Boolean get() = remoteChapters.isNotEmpty() &&
        (remoteChapters.substringBefore('/').toIntOrNull() ?: 0) > (savedChapters.substringBefore('/').toIntOrNull() ?: chapterCount)
}


/** Cached works on disk plus reading progress for every work that was opened. */
class Library(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val worksDir = File(context.filesDir, "works").apply { mkdirs() }
    private val libraryFile = File(context.filesDir, "library.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private val _entries = MutableStateFlow<Map<Long, LibraryEntry>>(emptyMap())
    val entries: StateFlow<Map<Long, LibraryEntry>> = _entries.asStateFlow()

    init {
        _entries.value = runCatching {
            json.decodeFromString<List<LibraryEntry>>(libraryFile.readText()).associateBy { it.workId }
        }.getOrDefault(emptyMap())
    }

    fun entry(id: Long): LibraryEntry? = _entries.value[id]

    suspend fun cachedWork(id: Long): Work? = withContext(Dispatchers.IO) {
        val f = File(worksDir, "$id.json")
        if (!f.exists()) null else runCatching { json.decodeFromString<Work>(f.readText()) }.getOrNull()
    }

    suspend fun saveWork(work: Work) = withContext(Dispatchers.IO) {
        val tmp = File(worksDir, "${work.id}.json.tmp")
        tmp.writeText(json.encodeToString(Work.serializer(), work))
        tmp.renameTo(File(worksDir, "${work.id}.json"))
        update(work.id) { e ->
            (e ?: LibraryEntry(work.id, work.title, work.authors, work.chapters.size, work.fandoms, addedAt = System.currentTimeMillis()))
                .copy(
                    authors = work.authors, chapterCount = work.chapters.size, fandoms = work.fandoms,
                    relationships = work.relationships,
                    savedChapters = work.chaptersText, savedUpdated = work.updated, remoteChapters = "",
                )
        }
    }

    fun update(id: Long, transform: (LibraryEntry?) -> LibraryEntry?) {
        _entries.update { map ->
            val next = transform(map[id])
            if (next == null) map - id else map + (id to next)
        }
        persist()
    }

    fun saveProgress(id: Long, chapterIndex: Int, blockIndex: Int, charOffset: Int, fraction: Float) {
        update(id) { e ->
            e?.copy(
                chapterIndex = chapterIndex,
                blockIndex = blockIndex,
                charOffset = charOffset,
                fraction = if (e.finished) maxOf(fraction, e.fraction) else fraction,
                lastReadAt = System.currentTimeMillis(),
            )
        }
    }

    /** Fandoms ordered by how many works in the library belong to them. */
    fun favoriteFandoms(limit: Int = 8): List<String> =
        _entries.value.values.flatMap { it.fandoms }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(limit).map { it.key }

    fun markFinished(id: Long, finished: Boolean) {
        update(id) { it?.copy(finished = finished, fraction = if (finished) 1f else it.fraction, lastReadAt = System.currentTimeMillis()) }
    }

    fun savedSizeBytes(id: Long): Long = File(worksDir, "$id.json").length()

    fun remove(id: Long) = removeAll(listOf(id))

    fun removeAll(ids: Collection<Long>) {
        _entries.update { it - ids.toSet() }
        persist()
        scope.launch { ids.forEach { File(worksDir, "$it.json").delete() } }
    }

    private fun persist() {
        val snapshot = _entries.value.values.toList()
        scope.launch {
            writeMutex.withLock {
                val tmp = File(libraryFile.path + ".tmp")
                tmp.writeText(json.encodeToString(snapshot))
                tmp.renameTo(libraryFile)
            }
        }
    }
}
