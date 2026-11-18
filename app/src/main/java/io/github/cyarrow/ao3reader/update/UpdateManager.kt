package io.github.cyarrow.ao3reader.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.cyarrow.ao3reader.BuildConfig
import io.github.cyarrow.ao3reader.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

data class UpdateInfo(
    val version: Version,
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
)

sealed interface CheckResult {
    data class Available(val info: UpdateInfo) : CheckResult
    data object UpToDate : CheckResult
    data object Disabled : CheckResult
    data class Failed(val message: String) : CheckResult
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(
    val name: String,
    val size: Long = 0,
    @SerialName("browser_download_url") val downloadUrl: String,
)

/** Checks GitHub Releases for a newer signed APK, downloads it and hands it to the system installer. */
class UpdateManager(
    private val context: Context,
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val currentVersion: String = BuildConfig.VERSION_NAME

    /** Debug builds have a different package id and signature, so they never self-update. */
    val isEnabled: Boolean = !BuildConfig.DEBUG

    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext CheckResult.Disabled
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            val release = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                json.decodeFromString<GitHubRelease>(resp.body.string())
            }
            settings.setLastUpdateCheck(System.currentTimeMillis())

            val latest = Version.parse(release.tagName)
                ?: return@withContext CheckResult.Failed("Bad tag ${release.tagName}")
            val current = Version.parse(currentVersion)
                ?: return@withContext CheckResult.Failed("Bad version $currentVersion")
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
            if (release.draft || release.prerelease || apk == null || latest <= current) {
                CheckResult.UpToDate
            } else {
                CheckResult.Available(
                    UpdateInfo(latest, release.tagName, release.body.orEmpty(), apk.downloadUrl, apk.size)
                )
            }
        } catch (e: Exception) {
            CheckResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Automatic check on launch: enabled in settings, at most once a day, and skipped versions are ignored. */
    suspend fun autoCheck(): UpdateInfo? {
        if (!isEnabled) return null
        val now = System.currentTimeMillis()
        if (now - settings.lastUpdateCheck() < 24 * 60 * 60 * 1000L) return null
        val result = check() as? CheckResult.Available ?: return null
        if (settings.skippedVersion() == result.info.version.toString()) return null
        return result.info
    }

    suspend fun skip(info: UpdateInfo) = settings.setSkippedVersion(info.version.toString())

    /** Downloads the APK into the cache, reporting progress in 0..100. */
    suspend fun download(info: UpdateInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "ao3reader-${info.version}.apk")
        val partial = File(dir, target.name + ".part")
        client.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body
            val total = body.contentLength().takeIf { it > 0 } ?: info.apkSize
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var lastPercent = -1
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) {
                            val percent = (read * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                withContext(Dispatchers.Main) { onProgress(percent) }
                            }
                        }
                    }
                }
            }
        }
        if (!partial.renameTo(target)) throw IOException("Cannot save APK")
        target
    }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
