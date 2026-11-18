package io.github.cyarrow.ao3reader.ui.reader

import android.app.Activity
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.cyarrow.ao3reader.AppContainer
import io.github.cyarrow.ao3reader.MainActivity
import io.github.cyarrow.ao3reader.R
import io.github.cyarrow.ao3reader.data.ReaderSettings
import io.github.cyarrow.ao3reader.data.ReadingMode
import io.github.cyarrow.ao3reader.reader.DisplayChapter
import io.github.cyarrow.ao3reader.reader.DisplayLabels
import io.github.cyarrow.ao3reader.reader.Page
import io.github.cyarrow.ao3reader.reader.Paginator
import io.github.cyarrow.ao3reader.reader.buildDisplay
import io.github.cyarrow.ao3reader.reader.indexOfPosition
import io.github.cyarrow.ao3reader.ui.common.errorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/** Reading position: chapter, display block within the chapter, and character offset in that block. */
data class Position(val chapter: Int, val block: Int, val char: Int = 0)

@Composable
fun ReaderScreen(container: AppContainer, workId: Long, onBack: () -> Unit, onOpenFonts: () -> Unit) {
    val vm: ReaderViewModel = viewModel(key = "reader-$workId") { ReaderViewModel(container, workId) }
    val state by vm.state.collectAsState()
    val settings by container.readerPrefs.settings.collectAsState(initial = null)
    val s = settings ?: return
    val colors = s.theme.colors()

    Box(Modifier.fillMaxSize().background(colors.background)) {
        when (val st = state) {
            ReaderState.Loading -> CenterMessage(colors) {
                CircularProgressIndicator(color = colors.accent)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.reader_loading), color = colors.secondary)
            }
            is ReaderState.Failed -> CenterMessage(colors) {
                Text(errorMessage(st.error), color = colors.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.load() }) { Text(stringResource(R.string.retry)) }
            }
            is ReaderState.Ready -> ReaderBody(container, vm, st, s, colors, onBack, onOpenFonts)
        }
    }
}

@Composable
private fun CenterMessage(colors: ReaderColors, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun ReaderBody(
    container: AppContainer,
    vm: ReaderViewModel,
    st: ReaderState.Ready,
    s: ReaderSettings,
    colors: ReaderColors,
    onBack: () -> Unit,
    onOpenFonts: () -> Unit,
) {
    val labels = DisplayLabels(
        summary = stringResource(R.string.reader_chapter_summary),
        notes = stringResource(R.string.reader_chapter_notes),
        endNotes = stringResource(R.string.reader_chapter_end_notes),
        workNotes = stringResource(R.string.reader_work_notes),
        workEndNotes = stringResource(R.string.reader_work_end_notes),
    )
    val chapters = remember(st.version, s.showNotes) { buildDisplay(st.work, s.showNotes, labels) }
    val installed by container.fonts.installed.collectAsState()
    val family = remember(s.fontId, s.fontWeight, installed) { container.fonts.family(s.fontId, s.fontWeight) }
    val density = LocalDensity.current
    val styles = remember(s, family, colors, density) { ReaderStyles(s, family, colors, density) }

    var position by remember {
        val e = vm.initialEntry
        val ch = (e?.chapterIndex ?: 0).coerceIn(0, chapters.lastIndex)
        mutableStateOf(Position(ch, e?.blockIndex ?: 0, e?.charOffset ?: 0))
    }
    var chromeVisible by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // Persist position (debounced) and on leaving.
    LaunchedEffect(Unit) {
        snapshotFlow { position }.debounce(600).collect { p -> vm.saveProgress(p.chapter, p.block, p.char, fraction(chapters, p)) }
    }
    DisposableEffect(Unit) {
        onDispose { vm.saveProgress(position.chapter, position.block, position.char, fraction(chapters, position)) }
    }

    SystemBars(chromeVisible, colors.isDark)
    KeepScreenOn(s.keepScreenOn)
    BackHandler(enabled = chromeVisible) { chromeVisible = false }

    // Volume keys / external navigation requests.
    var navRequest by remember { mutableIntStateOf(0) }
    var navCounter by remember { mutableIntStateOf(0) }
    VolumeKeys(s.volumeKeys) { forward -> navRequest = if (forward) 1 else -1; navCounter++ }

    val toggleChrome = { chromeVisible = !chromeVisible }
    val jumpToChapter = { index: Int -> position = Position(index.coerceIn(0, chapters.lastIndex), 0, 0) }

    Box(Modifier.fillMaxSize()) {
        when (s.mode) {
            ReadingMode.Scroll -> ScrollReader(
                chapters, styles, colors, s, position,
                onPosition = { position = it },
                onTap = toggleChrome,
                navRequest = navRequest, navCounter = navCounter,
            )
            ReadingMode.Paged -> PagedReader(
                chapters, styles, colors, s, position,
                onPosition = { position = it },
                onTap = toggleChrome,
                navRequest = navRequest, navCounter = navCounter,
            )
        }

        ReaderChrome(
            visible = chromeVisible,
            colors = colors,
            title = st.work.title,
            chapterTitle = chapters.getOrNull(position.chapter)?.title.orEmpty(),
            chapterLabel = stringResource(R.string.reader_chapter_of, position.chapter + 1, chapters.size),
            percent = (fraction(chapters, position) * 100).toInt(),
            hasPrev = position.chapter > 0,
            hasNext = position.chapter < chapters.lastIndex,
            onBack = onBack,
            onRefresh = { vm.load(forceRefresh = true) },
            onPrev = { jumpToChapter(position.chapter - 1) },
            onNext = { jumpToChapter(position.chapter + 1) },
            onToc = { showToc = true },
            onSettings = { showSettings = true; chromeVisible = false },
        )
    }

    if (showToc) {
        TocSheet(chapters, position.chapter, colors, onSelect = { jumpToChapter(it); showToc = false; chromeVisible = false }) {
            showToc = false
        }
    }
    if (showSettings) {
        ReaderSettingsSheet(container, s, st, colors, onWorkConvert = vm::setWorkConvert, onGlobalConvertChanged = vm::applyConversion,
            onOpenFonts = { showSettings = false; onOpenFonts() }) { showSettings = false }
    }
}

private fun fraction(chapters: List<DisplayChapter>, p: Position): Float {
    val total = chapters.sumOf { it.charCount }.coerceAtLeast(1)
    val before = chapters.take(p.chapter).sumOf { it.charCount }
    val inChapter = chapters.getOrNull(p.chapter)?.blocks?.take(p.block)?.sumOf { it.text.length } ?: 0
    return ((before + inChapter + p.char).toFloat() / total).coerceIn(0f, 1f)
}

// ------------------------------------------------------------------------------------------ scroll

@Composable
private fun ScrollReader(
    chapters: List<DisplayChapter>,
    styles: ReaderStyles,
    colors: ReaderColors,
    s: ReaderSettings,
    position: Position,
    onPosition: (Position) -> Unit,
    onTap: () -> Unit,
    navRequest: Int,
    navCounter: Int,
) {
    // Flatten every chapter into one list for continuous scrolling.
    val offsets = remember(chapters) { chapters.runningFold(0) { acc, c -> acc + c.blocks.size } }
    val items = remember(chapters) { chapters.flatMap { c -> c.blocks.indices.map { c.index to it } } }
    fun globalIndex(p: Position) = (offsets.getOrElse(p.chapter) { 0 } + p.block).coerceIn(0, (items.size - 1).coerceAtLeast(0))

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = globalIndex(position))
    var lastReported by remember { mutableStateOf(position) }
    // External jumps (TOC, chapter buttons): scroll when the position differs from what we reported.
    LaunchedEffect(position) {
        if (position != lastReported) {
            listState.scrollToItem(globalIndex(position))
            lastReported = position
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { idx ->
            val (ch, block) = items.getOrNull(idx) ?: return@collect
            val p = Position(ch, block, 0)
            lastReported = p
            onPosition(p)
        }
    }
    LaunchedEffect(navCounter) {
        if (navCounter == 0) return@LaunchedEffect
        val viewport = listState.layoutInfo.viewportSize.height
        listState.animateScrollBy(navRequest * viewport * 0.88f)
    }

    val margin = s.horizontalMargin.dp
    SelectionContainer {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
            contentPadding = PaddingValues(start = margin, end = margin, top = 40.dp, bottom = 120.dp),
        ) {
            itemsIndexed(items, key = { _, it -> "${it.first}-${it.second}" }) { _, (ch, block) ->
                ScrollBlock(chapters[ch].blocks[block], styles, colors)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ paged

@Composable
private fun PagedReader(
    chapters: List<DisplayChapter>,
    styles: ReaderStyles,
    colors: ReaderColors,
    s: ReaderSettings,
    position: Position,
    onPosition: (Position) -> Unit,
    onTap: () -> Unit,
    navRequest: Int,
    navCounter: Int,
) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val chapterIndex = position.chapter.coerceIn(0, chapters.lastIndex)
    val chapter = chapters[chapterIndex]

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = s.horizontalMargin.dp)
    ) {
        val headerH = 40.dp
        val footerH = 36.dp
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { (maxHeight - headerH - footerH).roundToPx() }

        val pages by produceState<List<Page>?>(null, chapter, styles, widthPx, heightPx) {
            value = null
            value = withContext(Dispatchers.Default) {
                Paginator(measurer, widthPx, heightPx, styles::style, styles::spacingAfterPx, styles.dividerHeightPx).paginate(chapter)
            }
        }
        val pageList = pages ?: run {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accent) }
            return@BoxWithConstraints
        }

        val hasPrev = chapterIndex > 0
        val hasNext = chapterIndex < chapters.lastIndex
        val lead = if (hasPrev) 1 else 0
        val startPage = if (position.block == Int.MAX_VALUE) pageList.lastIndex else pageList.indexOfPosition(position.block, position.char)

        key(chapterIndex, pageList) {
            val pagerState = rememberPagerState(initialPage = startPage + lead) { pageList.size + lead + (if (hasNext) 1 else 0) }
            val scope = rememberCoroutineScope()

            fun turn(delta: Int) {
                val target = pagerState.currentPage + delta
                scope.launch {
                    if (target in 0 until pagerState.pageCount) pagerState.animateScrollToPage(target)
                }
            }

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect { page ->
                    val idx = page - lead
                    when {
                        idx < 0 -> onPosition(Position(chapterIndex - 1, Int.MAX_VALUE, 0))
                        idx >= pageList.size -> onPosition(Position(chapterIndex + 1, 0, 0))
                        else -> {
                            val first = pageList[idx].first
                            onPosition(Position(chapterIndex, first.blockIndex, first.start))
                        }
                    }
                }
            }
            LaunchedEffect(navCounter) { if (navCounter != 0) turn(navRequest) }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { it },
            ) { page ->
                val idx = page - lead
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(colors.background)
                        .pointerInput(s.tapToTurn) {
                            detectTapGestures(onTap = { offset ->
                                val third = size.width / 3f
                                when {
                                    !s.tapToTurn -> onTap()
                                    offset.x < third -> turn(-1)
                                    offset.x > third * 2 -> turn(1)
                                    else -> onTap()
                                }
                            })
                        }
                ) {
                    Box(Modifier.height(headerH).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                        Text(
                            chapter.title, color = colors.secondary, fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            idx < 0 -> ChapterEdge(stringResource(R.string.reader_prev_chapter), chapters[chapterIndex - 1].title, colors)
                            idx >= pageList.size -> ChapterEdge(stringResource(R.string.reader_next_chapter), chapters[chapterIndex + 1].title, colors)
                            else -> SelectionContainer { PageView(chapter, pageList[idx], styles, colors) }
                        }
                    }
                    Row(Modifier.height(footerH).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Clock(colors)
                        Spacer(Modifier.weight(1f))
                        if (idx in pageList.indices) {
                            Text("${idx + 1} / ${pageList.size}", color = colors.secondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterEdge(label: String, title: String, colors: ReaderColors) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = colors.secondary, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, color = colors.text, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Clock(colors: ReaderColors) {
    val context = LocalContext.current
    val time by produceState(initialValue = "") {
        val format = android.text.format.DateFormat.getTimeFormat(context)
        while (true) {
            value = format.format(Date())
            delay(30_000)
        }
    }
    Text(time, color = colors.secondary, fontSize = 12.sp)
}

// ------------------------------------------------------------------------------------------ chrome

@Composable
private fun ReaderChrome(
    visible: Boolean,
    colors: ReaderColors,
    title: String,
    chapterTitle: String,
    chapterLabel: String,
    percent: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToc: () -> Unit,
    onSettings: () -> Unit,
) {
    val bar = colors.background.copy(alpha = 0.97f)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(bar).windowInsetsPadding(WindowInsets.statusBars).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = colors.text)
                }
                Column(Modifier.weight(1f)) {
                    Text(title, color = colors.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(chapterTitle, color = colors.secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, stringResource(R.string.reader_refresh), tint = colors.text)
                }
            }
        }
        AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth().background(bar).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    "$chapterLabel · $percent%", color = colors.secondary, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    ChromeButton(Icons.Filled.ChevronLeft, stringResource(R.string.reader_prev_chapter), colors, hasPrev, onPrev)
                    ChromeButton(Icons.AutoMirrored.Filled.List, stringResource(R.string.reader_toc), colors, true, onToc)
                    ChromeButton(Icons.Filled.TextFields, stringResource(R.string.reader_appearance), colors, true, onSettings)
                    ChromeButton(Icons.Filled.ChevronRight, stringResource(R.string.reader_next_chapter), colors, hasNext, onNext)
                }
            }
        }
    }
}

@Composable
private fun ChromeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    colors: ReaderColors,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) colors.text else colors.secondary.copy(alpha = 0.4f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label, tint = tint, modifier = Modifier.size(26.dp)) }
        Text(label, color = tint, fontSize = 11.sp)
    }
}

// ------------------------------------------------------------------------------------------ system integration

@Composable
private fun SystemBars(chromeVisible: Boolean, dark: Boolean) {
    val view = LocalView.current
    val activity = view.context as? Activity ?: return
    DisposableEffect(chromeVisible, dark) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
        if (chromeVisible) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {}
    }
    DisposableEffect(Unit) {
        onDispose {
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun VolumeKeys(enabled: Boolean, onTurn: (forward: Boolean) -> Unit) {
    val activity = LocalContext.current as? MainActivity ?: return
    DisposableEffect(enabled) {
        activity.keyHandler = if (!enabled) null else { event ->
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (event.action == KeyEvent.ACTION_DOWN) onTurn(event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                    true
                }
                else -> false
            }
        }
        onDispose { activity.keyHandler = null }
    }
}
