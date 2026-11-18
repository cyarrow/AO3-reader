package io.github.cyarrow.ao3reader

import android.app.Application
import io.github.cyarrow.ao3reader.data.SettingsRepository
import io.github.cyarrow.ao3reader.update.UpdateManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AO3ReaderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Simple manual dependency container shared across the app. */
class AppContainer(app: Application) {
    val httpClient: OkHttpClient = OkHttpClient.Builder()
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
}
