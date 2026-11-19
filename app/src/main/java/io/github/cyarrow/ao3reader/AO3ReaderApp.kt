package io.github.cyarrow.ao3reader

import android.app.Application
import io.github.cyarrow.ao3reader.ao3.Ao3Client
import io.github.cyarrow.ao3reader.ao3.WorkSummary
import io.github.cyarrow.ao3reader.data.Library
import io.github.cyarrow.ao3reader.data.ReaderPreferences
import io.github.cyarrow.ao3reader.data.SettingsRepository
import io.github.cyarrow.ao3reader.fonts.FontRepository
import io.github.cyarrow.ao3reader.text.ChineseConverter
import io.github.cyarrow.ao3reader.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AO3ReaderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Load the Simplified/Traditional dictionaries in the background so opening a work never waits on them.
        kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch { runCatching { container.converter() } }
    }
}

/** Simple manual dependency container shared across the app. */
class AppContainer(private val app: Application) {
    val cookieJar = io.github.cyarrow.ao3reader.account.PersistentCookieJar(
        runCatching { io.github.cyarrow.ao3reader.account.SecureStore(app, "cookies") }.getOrNull()
    )

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "AO3Reader/${BuildConfig.VERSION_NAME} (Android)")
                    .build()
            )
        }
        .build()

    val settings = SettingsRepository(app)
    val updateManager = UpdateManager(app, httpClient, settings)
    val ao3 = Ao3Client(httpClient)
    val account = io.github.cyarrow.ao3reader.account.AccountManager(
        ao3, cookieJar, io.github.cyarrow.ao3reader.account.SecureStore(app, "account"),
    )
    val library = Library(app)
    val works = io.github.cyarrow.ao3reader.data.WorkRepository(ao3, library)
    val readerPrefs = ReaderPreferences(app)
    val fonts = FontRepository(app, httpClient)

    /** Search results seen recently, so the work page can show details before the full work loads. */
    val summaries = ConcurrentHashMap<Long, WorkSummary>()

    /** A search requested from elsewhere (e.g. tapping a tag); consumed by the next search screen. */
    @Volatile var pendingSearch: io.github.cyarrow.ao3reader.ao3.SearchQuery? = null

    private val converterLock = Mutex()
    private var converterInstance: ChineseConverter? = null

    /** Loads the OpenCC dictionaries once, off the main thread. */
    suspend fun converter(): ChineseConverter = converterLock.withLock {
        converterInstance ?: withContext(Dispatchers.Default) {
            ChineseConverter.fromStreams { app.assets.open("opencc/$it") }
        }.also { converterInstance = it }
    }
}
