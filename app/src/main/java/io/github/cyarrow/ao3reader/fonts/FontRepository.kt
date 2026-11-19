package io.github.cyarrow.ao3reader.fonts

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.cyarrow.ao3reader.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** A font file; [sha256] pins the exact bytes so a changed or tampered download is rejected. */
data class FontFile(val weight: Int, val fileName: String, val url: String, val sha256: String = "")

data class FontSpec(
    val id: String,
    val name: String,
    val description: String,
    val kind: Kind,
    /** True when the font has a weight axis, so any weight 100..900 can be rendered. */
    val variable: Boolean = false,
    val files: List<FontFile> = emptyList(),
    val downloadSizeMb: Int = 0,
) {
    enum class Kind { SystemSans, SystemSerif, Bundled, Download, Custom }
}

// Pinned google/fonts commit: downloads never change underneath us.
private const val GF = "https://raw.githubusercontent.com/google/fonts/cc5661d43cd4f9aad5567033a8e15ca28dc35c86/ofl"

class FontRepository(private val context: Context, private val client: OkHttpClient) {
    private val dir = File(context.filesDir, "fonts").apply { mkdirs() }

    val builtIn: List<FontSpec> = listOf(
        FontSpec("noto_serif_tc", "思源宋體", "Noto Serif TC．內建，可調粗細", FontSpec.Kind.Bundled, variable = true),
        FontSpec("system_sans", "系統黑體", "System sans-serif", FontSpec.Kind.SystemSans),
        FontSpec("system_serif", "系統明體", "System serif", FontSpec.Kind.SystemSerif),
        FontSpec(
            "lxgw_wenkai_tc", "霞鶩文楷", "LXGW WenKai TC．楷書風，溫和易讀", FontSpec.Kind.Download,
            files = listOf(
                FontFile(300, "LXGWWenKaiTC-Light.ttf", "$GF/lxgwwenkaitc/LXGWWenKaiTC-Light.ttf", "48cabec60b908f145002192c366d805b674d6e890a98c24f8a39fd33542273fd"),
                FontFile(400, "LXGWWenKaiTC-Regular.ttf", "$GF/lxgwwenkaitc/LXGWWenKaiTC-Regular.ttf", "4fcc5aec11cbbf737b0cfab7b63796f7f280087a7a656b2f13342cd5e5318d95"),
                FontFile(700, "LXGWWenKaiTC-Bold.ttf", "$GF/lxgwwenkaitc/LXGWWenKaiTC-Bold.ttf", "5c9feadfd928f3ae3860e7d001ad3d0d0c55b0157fff08cd4d51b51aca677f23"),
            ),
            downloadSizeMb = 39,
        ),
        FontSpec(
            "noto_sans_tc", "思源黑體", "Noto Sans TC．可調粗細", FontSpec.Kind.Download, variable = true,
            files = listOf(FontFile(400, "NotoSansTC-VF.ttf", "$GF/notosanstc/NotoSansTC%5Bwght%5D.ttf", "864727d210d54f2537bbe23b3a839436c3992af72de9322af5270897246bd44f")),
            downloadSizeMb = 12,
        ),
        FontSpec(
            "iansui", "芫荽", "Iansui．圓潤手寫風", FontSpec.Kind.Download,
            files = listOf(FontFile(400, "Iansui-Regular.ttf", "$GF/iansui/Iansui-Regular.ttf", "6e6340d80d618a42b48ade9370c34fa37a8210750c6fbc8efe65f23716538a2b")),
            downloadSizeMb = 9,
        ),
    )

    private val _custom = MutableStateFlow(loadCustom())
    val custom: StateFlow<List<FontSpec>> = _custom.asStateFlow()

    private val _installed = MutableStateFlow(computeInstalled())
    val installed: StateFlow<Set<String>> = _installed.asStateFlow()

    fun all(): List<FontSpec> = builtIn + _custom.value
    fun find(id: String): FontSpec? = all().firstOrNull { it.id == id }

    private fun loadCustom(): List<FontSpec> =
        File(dir, "custom").apply { mkdirs() }.listFiles()?.sortedBy { it.name }?.map { f ->
            FontSpec("custom:${f.name}", f.nameWithoutExtension, f.name, FontSpec.Kind.Custom,
                files = listOf(FontFile(400, "custom/${f.name}", "")))
        }.orEmpty()

    private fun computeInstalled(): Set<String> = all().filter { isInstalled(it) }.map { it.id }.toSet()

    fun isInstalled(spec: FontSpec): Boolean = when (spec.kind) {
        FontSpec.Kind.Download -> spec.files.all { File(dir, it.fileName).exists() }
        else -> true
    }

    suspend fun download(spec: FontSpec, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val total = spec.files.size
        spec.files.forEachIndexed { i, file ->
            val target = File(dir, file.fileName)
            if (target.exists()) return@forEachIndexed
            val part = File(dir, file.fileName + ".part")
            client.newCall(Request.Builder().url(file.url).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val length = resp.body.contentLength()
                resp.body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            read += n
                            if (length > 0) onProgress((i + read.toFloat() / length) / total)
                        }
                    }
                }
            }
            if (file.sha256.isNotEmpty()) {
                val actual = part.inputStream().use { input ->
                    val md = java.security.MessageDigest.getInstance("SHA-256")
                    val buf = ByteArray(64 * 1024)
                    while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
                    md.digest().joinToString("") { "%02x".format(it) }
                }
                if (actual != file.sha256) { part.delete(); throw IOException("Font checksum mismatch") }
            }
            if (!part.renameTo(target)) throw IOException("Cannot save font")
        }
        _installed.value = computeInstalled()
    }

    fun delete(spec: FontSpec) {
        spec.files.forEach { File(dir, it.fileName).delete() }
        if (spec.kind == FontSpec.Kind.Custom) _custom.value = loadCustom()
        _installed.value = computeInstalled()
    }

    suspend fun import(uri: Uri, displayName: String?): FontSpec = withContext(Dispatchers.IO) {
        val name = (displayName ?: "font-${System.currentTimeMillis()}.ttf")
            .replace(Regex("[^A-Za-z0-9._\\-\\u4e00-\\u9fff]"), "_")
            .let { if (it.endsWith(".ttf", true) || it.endsWith(".otf", true)) it else "$it.ttf" }
        val target = File(File(dir, "custom"), name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: throw IOException("Cannot open file")
        // Validate that Android can actually load it.
        runCatching { android.graphics.Typeface.createFromFile(target) }.getOrElse {
            target.delete(); throw IOException("Not a valid font")
        }
        _custom.value = loadCustom()
        _installed.value = computeInstalled()
        find("custom:$name")!!
    }

    /**
     * Builds a family where [FontWeight.Normal]-ish text renders at [weight], and bold spans
     * render noticeably heavier.
     */
    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    fun family(id: String, weight: Int): FontFamily {
        val spec = find(id)?.takeIf { isInstalled(it) } ?: builtIn.first()
        val bold = (weight + 300).coerceAtMost(900)
        return when (spec.kind) {
            FontSpec.Kind.SystemSans -> FontFamily.SansSerif
            FontSpec.Kind.SystemSerif -> FontFamily.Serif
            FontSpec.Kind.Bundled -> FontFamily(
                Font(R.font.noto_serif_tc, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight))),
                Font(R.font.noto_serif_tc, FontWeight(bold), variationSettings = FontVariation.Settings(FontVariation.weight(bold))),
            )
            else -> {
                val files = spec.files.map { it to File(dir, it.fileName) }
                if (spec.variable) {
                    val f = files.first().second
                    FontFamily(
                        Font(f, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight))),
                        Font(f, FontWeight(bold), variationSettings = FontVariation.Settings(FontVariation.weight(bold))),
                    )
                } else {
                    // Static weights: map the nearest file to the requested weight, and the heaviest to bold.
                    val nearest = files.minBy { kotlin.math.abs(it.first.weight - weight) }
                    val heaviest = files.maxBy { it.first.weight }
                    FontFamily(
                        Font(nearest.second, FontWeight(weight)),
                        Font(heaviest.second, FontWeight(bold)),
                    )
                }
            }
        }
    }

    /** Whether the weight slider has any effect for this font. */
    fun supportsWeight(id: String): Boolean {
        val spec = find(id) ?: return false
        return spec.variable || spec.kind == FontSpec.Kind.Bundled || spec.files.size > 1
    }
}
