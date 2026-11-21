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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
/** [atEnd] is true once the reader has reached the very end of the work (counts as 100%). */
data class Position(val chapter: Int, val block: Int, val char: Int = 0, val atEnd: Boolean = false)

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
            is ReaderState.Ready -> androidx.compose.material3.MaterialTheme(colorScheme = colors.materialScheme()) {
                ReaderBody(container, vm, st, s, colors, onBack, onOpenFonts)
            }
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
        chapterNumber = chapterFormat(),
        downloading = stringResource(R.string.reader_downloading_rest),
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
    Brightness(s.brightness)
    BackHandler(enabled = chromeVisible) { chromeVisible = false }

    // Volume keys / external navigation requests.
    var navRequest by remember { mutableIntStateOf(0) }
    var navCounter by remember { mutableIntStateOf(0) }
    VolumeKeys(s.volumeKeys) { forward -> navRequest = if (forward) 1 else -1; navCounter++ }

    val entries by container.library.entries.collectAsState()
    val finished = entries[st.work.id]?.finished == true
    val finishWork = {
        position = position.copy(atEnd = true)
        container.library.markFinished(st.work.id, true)
    }
    val toggleChrome = { chromeVisible = !chromeVisible }
    val jumpToChapter = { index: Int -> position = Position(index.coerceIn(0, chapters.lastIndex), 0, 0) }

    Box(Modifier.fillMaxSize()) {
        when (s.mode) {
            ReadingMode.Scroll -> ScrollReader(
                chapters, styles, colors, s, position,
                onPosition = { position = it },
                onTap = toggleChrome,
                navRequest = navRequest, navCounter = navCounter,
                finished = finished, onNextChapter = { jumpToChapter(it) }, onFinishWork = finishWork,
            )
            ReadingMode.Paged -> PagedReader(
                chapters, styles, colors, s, position,
                onPosition = { position = it },
                onTap = toggleChrome,
                navRequest = navRequest, navCounter = navCounter,
                finished = finished, onNextChapter = { jumpToChapter(it) }, onFinishWork = finishWork,
            )
        }

        ReaderChrome(
            visible = chromeVisible,
            colors = colors,
            title = st.work.title,
            chapterTitle = chapters.getOrNull(position.chapter)?.title.orEmpty(),
            chapterLabel = stringResource(R.string.reader_chapter_of, position.chapter + 1, chapters.size),
            percent = if (finished) 100 else (fraction(chapters, position) * 100).toInt(),
            minutesLeft = minutesLeftInChapter(chapters, position),
            blockCount = chapters.getOrNull(position.chapter)?.blocks?.size ?: 1,
            block = position.block.coerceAtMost((chapters.getOrNull(position.chapter)?.blocks?.size ?: 1) - 1),
            onSeek = { b -> position = Position(position.chapter, b, 0) },
            hasPrev = position.chapter > 0,
            hasNext = position.chapter < chapters.lastIndex,
            onBack = onBack,
            onRefresh = { vm.load(forceRefresh = true) },
            onPrev = { jumpToChapter(position.chapter - 1) },
            onNext = { jumpToChapter(position.chapter + 1) },
            onToc = { showToc = true },
            onSettings = { showSettings = true; chromeVisible = false },
        )

        if (s.mode == ReadingMode.Paged && s.tapToTurn && !s.tapHintShown) {
            val scope = rememberCoroutineScope()
            TapHint(colors) { scope.launch { container.readerPrefs.update { it.copy(tapHintShown = true) } } }
        }
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

/** Remaining reading time in the current chapter (about 400 CJK characters a minute). */
private fun minutesLeftInChapter(chapters: List<DisplayChapter>, p: Position): Int {
    val ch = chapters.getOrNull(p.chapter) ?: return 0
    val remaining = ch.blocks.drop(p.block.coerceAtMost(ch.blocks.size)).sumOf { it.text.length } - p.char
    return ((remaining.coerceAtLeast(0) + 399) / 400)
}

@Composable
fun chapterFormat(): (Int) -> String {
    val format = stringResource(R.string.reader_chapter_number)
    return remember(format) { { n: Int -> format.format(n) } }
}

private fun fraction(chapters: List<DisplayChapter>, p: Position): Float {
    if (p.atEnd) return 1f
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
    finished: Boolean,
    onNextChapter: (Int) -> Unit,
    onFinishWork: () -> Unit,
) {
    // Flatten every chapter into one list for continuous scrolling; block index -1 is the chapter's end actions.
    val offsets = remember(chapters) { chapters.runningFold(0) { acc, c -> acc + c.blocks.size + 1 } }
    val items = remember(chapters) { chapters.flatMap { c -> c.blocks.indices.map { c.index to it } + (c.index to -1) } }
    fun globalIndex(p: Position) = (offsets.getOrElse(p.chapter) { 0 } + p.block.coerceAtLeast(0)).coerceIn(0, (items.size - 1).coerceAtLeast(0))

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
        snapshotFlow { listState.firstVisibleItemIndex to !listState.canScrollForward }.collect { (idx, bottom) ->
            val (ch, rawBlock) = items.getOrNull(idx) ?: return@collect
            val block = if (rawBlock < 0) (chapters[ch].blocks.size - 1).coerceAtLeast(0) else rawBlock
            // The list holds the whole work, so hitting the bottom means the work is finished.
            val p = Position(ch, block, 0, atEnd = bottom && items.isNotEmpty())
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
    Column(Modifier.fillMaxSize()) {
        SelectionContainer(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
                contentPadding = PaddingValues(start = margin, end = margin, top = 40.dp, bottom = 96.dp),
            ) {
                itemsIndexed(items, key = { _, it -> "${it.first}-${it.second}" }) { i, (ch, block) ->
                    if (block < 0) {
                        ChapterEndActions(
                            isLast = ch == chapters.lastIndex, finished = finished,
                            nextTitle = chapters.getOrNull(ch + 1)?.title, colors = colors,
                            onNextChapter = { onNextChapter(ch + 1) }, onFinishWork = onFinishWork,
                        )
                        return@itemsIndexed
                    }
                    val b = chapters[ch].blocks[block]
                    // Breathing room before every chapter opening after the first.
                    if (i > 0 && block == 0) Spacer(Modifier.height((s.fontSize * 4).dp))
                    ScrollBlock(b, styles, colors)
                }
            }
        }
        // Quiet status line, like a running foot in a book.
        val current = chapters.getOrNull(position.chapter)
        Row(
            Modifier.fillMaxWidth().background(colors.background).padding(horizontal = margin).height(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                current?.title.orEmpty(), color = colors.secondary, fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(if (finished) stringResource(R.string.progress_finished) else "${(fraction(chapters, position) * 100).toInt()}%",
                color = colors.secondary, fontSize = 11.sp)
            Spacer(Modifier.width(12.dp))
            Clock(colors, 11)
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
    finished: Boolean,
    onNextChapter: (Int) -> Unit,
    onFinishWork: () -> Unit,
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
            // Trailing page after the last text page: end-of-chapter / end-of-work actions.
            val pagerState = rememberPagerState(initialPage = startPage + lead) { pageList.size + lead + 1 }
            val scope = rememberCoroutineScope()

            fun turn(delta: Int) {
                val target = pagerState.currentPage + delta
                when {
                    target in 0 until pagerState.pageCount -> scope.launch { pagerState.animateScrollToPage(target) }
                    target >= pagerState.pageCount && hasNext -> onNextChapter(chapterIndex + 1)
                }
            }

            // Positions this pager reported itself must not be treated as external jumps.
            var lastReported by remember { mutableStateOf<Position?>(null) }
            fun report(p: Position) { lastReported = p; onPosition(p) }
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect { page ->
                    val idx = page - lead
                    when {
                        idx < 0 -> report(Position(chapterIndex - 1, Int.MAX_VALUE, 0))
                        // Reaching the end page of the last chapter counts as the end of the work.
                        idx >= pageList.size -> if (!hasNext) {
                            val last = pageList.last().slices.last()
                            report(Position(chapterIndex, last.blockIndex, last.start, atEnd = true))
                        }
                        else -> {
                            val first = pageList[idx].first
                            val last = chapterIndex == chapters.lastIndex && idx == pageList.lastIndex
                            report(Position(chapterIndex, first.blockIndex, first.start, atEnd = last))
                        }
                    }
                }
            }
            LaunchedEffect(navCounter) { if (navCounter != 0) turn(navRequest) }
            // External jumps inside this chapter (progress slider).
            LaunchedEffect(position.block, position.char) {
                if (position.block == Int.MAX_VALUE || position == lastReported) return@LaunchedEffect
                val idx = pageList.indexOfPosition(position.block, position.char) + lead
                val shownIdx = pagerState.settledPage - lead
                val shownFirst = pageList.getOrNull(shownIdx)?.first
                val sameAsShown = shownFirst != null && pageList.indexOfPosition(shownFirst.blockIndex, shownFirst.start) + lead == idx
                if (!sameAsShown && idx != pagerState.currentPage) pagerState.scrollToPage(idx)
            }

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
                        // The chapter's opening page already shows its title in full.
                        if (idx > 0) Text(
                            chapter.title, color = colors.secondary, fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            idx < 0 -> ChapterEdge(stringResource(R.string.reader_prev_chapter), chapters[chapterIndex - 1].title, colors)
                            idx >= pageList.size -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                ChapterEndActions(
                                    isLast = !hasNext, finished = finished,
                                    nextTitle = chapters.getOrNull(chapterIndex + 1)?.title, colors = colors,
                                    onNextChapter = { onNextChapter(chapterIndex + 1) }, onFinishWork = onFinishWork,
                                )
                            }
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

/**
 * Shown after each chapter: "本章讀完 → 下一章", or on the last chapter "作品讀完了", which marks the
 * work finished (100%) regardless of scroll position.
 */
@Composable
private fun ChapterEndActions(
    isLast: Boolean,
    finished: Boolean,
    nextTitle: String?,
    colors: ReaderColors,
    onNextChapter: () -> Unit,
    onFinishWork: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.width(48.dp).height(1.dp).background(colors.secondary.copy(alpha = 0.4f)))
        Spacer(Modifier.height(24.dp))
        if (!isLast) {
            Button(onClick = onNextChapter, modifier = Modifier.height(48.dp)) {
                Text(stringResource(R.string.reader_chapter_done))
            }
            if (nextTitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(nextTitle, color = colors.secondary, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
        } else if (!finished) {
            Button(onClick = onFinishWork, modifier = Modifier.height(48.dp)) {
                Text(stringResource(R.string.reader_work_done))
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.reader_work_done_desc), color = colors.secondary, fontSize = 13.sp, textAlign = TextAlign.Center)
        } else {
            Text(stringResource(R.string.reader_work_finished), color = colors.accent, fontSize = 16.sp, textAlign = TextAlign.Center)
        }
    }
}

/** One-time explanation of the paged-mode tap zones. */
@Composable
private fun TapHint(colors: ReaderColors, onDismiss: () -> Unit) {
    // A near-opaque scrim so the zone labels never compete with the page text.
    val scrim = Color(0xFF15120F).copy(alpha = 0.88f)
    Row(Modifier.fillMaxSize().background(scrim).pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
        listOf(R.string.reader_tap_hint_left, R.string.reader_tap_hint_center, R.string.reader_tap_hint_right).forEachIndexed { i, label ->
            Box(
                Modifier.weight(1f).fillMaxSize()
                    .background(if (i == 1) Color.White.copy(alpha = 0.07f) else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        when (i) { 0 -> Icons.Filled.ChevronLeft; 1 -> Icons.Filled.TextFields; else -> Icons.Filled.ChevronRight },
                        null, tint = Color.White, modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(label), color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().padding(bottom = 72.dp), contentAlignment = Alignment.BottomCenter) {
        Button(onClick = onDismiss, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.reader_tap_hint_ok)) }
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
private fun Clock(colors: ReaderColors, sizeSp: Int = 12) {
    val context = LocalContext.current
    val time by produceState(initialValue = "") {
        val format = android.text.format.DateFormat.getTimeFormat(context)
        while (true) {
            value = format.format(Date())
            delay(30_000)
        }
    }
    Text(time, color = colors.secondary, fontSize = sizeSp.sp)
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
    minutesLeft: Int,
    blockCount: Int,
    block: Int,
    onSeek: (Int) -> Unit,
    hasPrev: Boolean,
    hasNext: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToc: () -> Unit,
    onSettings: () -> Unit,
) {
    val bar = colors.background
    val line = colors.secondary.copy(alpha = 0.18f)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)) {
            Column(Modifier.fillMaxWidth().shadow(6.dp).background(bar)) {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(4.dp),
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
            HorizontalDivider(color = line)
            }
        }
        AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth().shadow(6.dp).background(bar).windowInsetsPadding(WindowInsets.navigationBars)) {
                HorizontalDivider(color = line)
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(chapterLabel, color = colors.text, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (minutesLeft <= 1) stringResource(R.string.reader_chapter_almost_done)
                        else stringResource(R.string.reader_minutes_left, minutesLeft),
                        color = colors.secondary, fontSize = 12.sp,
                    )
                    Text(" · $percent%", color = colors.secondary, fontSize = 12.sp)
                }
                if (blockCount > 1) {
                    Slider(
                        value = block.toFloat(),
                        onValueChange = { onSeek(it.toInt()) },
                        valueRange = 0f..(blockCount - 1).toFloat(),
                        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent,
                            inactiveTrackColor = colors.secondary.copy(alpha = 0.25f)),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    ChromeButton(Icons.Filled.ChevronLeft, stringResource(R.string.reader_prev_chapter), colors, hasPrev, onPrev)
                    ChromeButton(Icons.AutoMirrored.Filled.List, stringResource(R.string.reader_toc), colors, true, onToc)
                    ChromeButton(Icons.Filled.TextFields, stringResource(R.string.reader_appearance), colors, true, onSettings)
                    ChromeButton(Icons.Filled.ChevronRight, stringResource(R.string.reader_next_chapter), colors, hasNext, onNext)
                }
                Spacer(Modifier.height(6.dp))
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
    // Icon and label form one large touch target.
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.padding(4.dp).size(24.dp))
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
private fun Brightness(value: Float) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(value) {
        val window = activity.window
        window.attributes = window.attributes.apply {
            screenBrightness = if (value < 0f) android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else value.coerceIn(0.01f, 1f)
        }
        onDispose {
            window.attributes = window.attributes.apply {
                screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
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
