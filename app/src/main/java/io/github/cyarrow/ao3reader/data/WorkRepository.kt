package io.github.cyarrow.ao3reader.data

import io.github.cyarrow.ao3reader.ao3.Ao3Client
import io.github.cyarrow.ao3reader.ao3.Work
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single entry point for loading works. Concurrent requests for the same work share one download
 * (the work page and the reader used to fetch it twice), and a one-chapter preview is published
 * as soon as the work page arrives.
 */
class WorkRepository(private val ao3: Ao3Client, private val library: Library) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inflight = HashMap<Long, Deferred<Work>>()

    private val _previews = MutableStateFlow<Map<Long, Work>>(emptyMap())
    /** Partial works (first chapter only) for downloads still in progress. */
    val previews: StateFlow<Map<Long, Work>> = _previews.asStateFlow()

    suspend fun load(id: Long, refresh: Boolean = false): Work {
        if (!refresh) library.cachedWork(id)?.let { return it }
        return download(id).await()
    }

    fun isDownloading(id: Long): Boolean = synchronized(inflight) { inflight.containsKey(id) }

    private fun download(id: Long): Deferred<Work> = synchronized(inflight) {
        inflight[id] ?: scope.async {
            try {
                ao3.fetchWork(id) { preview -> _previews.update { it + (id to preview) } }
                    .also { library.saveWork(it) }
            } finally {
                synchronized(inflight) { inflight.remove(id) }
                _previews.update { it - id }
            }
        }.also { inflight[id] = it }
    }
}
