package io.github.cyarrow.ao3reader.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cyarrow.ao3reader.AppContainer
import io.github.cyarrow.ao3reader.R
import io.github.cyarrow.ao3reader.data.ConvertMode
import io.github.cyarrow.ao3reader.data.ReaderSettings
import io.github.cyarrow.ao3reader.data.ReaderTheme
import io.github.cyarrow.ao3reader.data.ReadingMode
import io.github.cyarrow.ao3reader.reader.DisplayChapter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocSheet(chapters: List<DisplayChapter>, current: Int, colors: ReaderColors, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.background, contentColor = colors.text) {
        Text(
            stringResource(R.string.reader_toc), fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = colors.text,
        )
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
        LazyColumn(state = listState, modifier = Modifier.padding(bottom = 24.dp)) {
            itemsIndexed(chapters) { i, ch ->
                val selected = i == current
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(i) }
                        .background(if (selected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        ch.title, color = if (selected) colors.accent else colors.text, fontSize = 15.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    container: AppContainer,
    s: ReaderSettings,
    st: ReaderState.Ready,
    colors: ReaderColors,
    onWorkConvert: (ConvertMode?) -> Unit,
    onGlobalConvertChanged: () -> Unit,
    onOpenFonts: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val update: ((ReaderSettings) -> ReaderSettings) -> Unit = { f -> scope.launch { container.readerPrefs.update(f) } }
    val installed by container.fonts.installed.collectAsState()
    val custom by container.fonts.custom.collectAsState()
    val fonts = (container.fonts.builtIn + custom).filter { it.id in installed }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val sliderColors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent,
        inactiveTrackColor = colors.secondary.copy(alpha = 0.3f))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = androidx.compose.ui.graphics.lerp(colors.background, colors.text, 0.05f),
        tonalElevation = 0.dp,
        contentColor = colors.text,
        scrimColor = Color.Transparent,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            // Brightness (in-app, so night reading doesn't need the system panel)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(stringResource(R.string.reader_brightness), colors, Modifier.weight(1f))
                Text(stringResource(R.string.reader_brightness_system), color = colors.secondary, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 10.dp, end = 8.dp))
                Switch(s.brightness < 0f, { follow -> update { it.copy(brightness = if (follow) -1f else 0.5f) } },
                    modifier = Modifier.padding(top = 8.dp))
            }
            if (s.brightness >= 0f) {
                Slider(s.brightness, { v -> update { it.copy(brightness = v) } }, valueRange = 0.01f..1f, colors = sliderColors)
            }

            // Font size
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(stringResource(R.string.reader_font_size), colors, Modifier.weight(1f))
                FilledTonalIconButton(onClick = { update { it.copy(fontSize = (it.fontSize - 1).coerceAtLeast(12f)) } }) {
                    Icon(Icons.Filled.Remove, null)
                }
                Text(s.fontSize.roundToInt().toString(), fontSize = 16.sp, modifier = Modifier.width(44.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = colors.text)
                FilledTonalIconButton(onClick = { update { it.copy(fontSize = (it.fontSize + 1).coerceAtMost(36f)) } }) {
                    Icon(Icons.Filled.Add, null)
                }
            }

            // Fonts
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(stringResource(R.string.reader_font), colors, Modifier.weight(1f))
                TextButton(onClick = onOpenFonts) { Text(stringResource(R.string.reader_more_fonts), color = colors.accent) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                fonts.forEach { f ->
                    val selected = f.id == s.fontId
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.secondary.copy(alpha = 0.4f)), RoundedCornerShape(10.dp))
                            .clickable { update { it.copy(fontId = f.id) } }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(f.name, fontFamily = container.fonts.family(f.id, 400), color = colors.text, fontSize = 15.sp)
                    }
                }
            }

            if (container.fonts.supportsWeight(s.fontId)) {
                SliderRow(stringResource(R.string.reader_font_weight), s.fontWeight.toString(), colors) {
                    Slider(s.fontWeight.toFloat(), { v -> update { it.copy(fontWeight = (v / 50).roundToInt() * 50) } },
                        valueRange = 200f..800f, colors = sliderColors)
                }
            }
            SliderRow(stringResource(R.string.reader_line_height), "%.1f".format(s.lineHeight), colors) {
                Slider(s.lineHeight, { v -> update { it.copy(lineHeight = (v * 10).roundToInt() / 10f) } },
                    valueRange = 1.2f..2.6f, colors = sliderColors)
            }
            SliderRow(stringResource(R.string.reader_paragraph_spacing), "%.1f".format(s.paragraphSpacing), colors) {
                Slider(s.paragraphSpacing, { v -> update { it.copy(paragraphSpacing = (v * 10).roundToInt() / 10f) } },
                    valueRange = 0f..2f, colors = sliderColors)
            }
            SliderRow(stringResource(R.string.reader_margin), "${s.horizontalMargin}", colors) {
                Slider(s.horizontalMargin.toFloat(), { v -> update { it.copy(horizontalMargin = v.roundToInt()) } },
                    valueRange = 8f..56f, colors = sliderColors)
            }

            // Theme
            Label(stringResource(R.string.reader_theme), colors)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                ReaderTheme.entries.forEach { t ->
                    val c = t.colors()
                    val selected = t == s.theme
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(c.background)
                            .border(if (selected) 3.dp else 1.dp, if (selected) colors.accent else colors.secondary.copy(alpha = 0.4f), CircleShape)
                            .clickable { update { it.copy(theme = t) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Filled.Check, null, tint = c.text) else Text("文", color = c.text, fontSize = 15.sp)
                    }
                }
            }

            // Mode
            Label(stringResource(R.string.reader_mode), colors)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                val modes = listOf(ReadingMode.Scroll to R.string.reader_mode_scroll, ReadingMode.Paged to R.string.reader_mode_paged)
                modes.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = s.mode == mode,
                        onClick = { update { it.copy(mode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    ) { Text(stringResource(label)) }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = colors.secondary.copy(alpha = 0.2f))

            // Simplified → Traditional for this work
            Label(stringResource(R.string.reader_convert_this_work), colors)
            if (st.simplifiedSource) {
                Text(stringResource(R.string.reader_detected_simplified), color = colors.secondary, fontSize = 12.sp)
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                val options = listOf(null to R.string.reader_convert_follow, ConvertMode.On to R.string.reader_convert_on, ConvertMode.Off to R.string.reader_convert_off)
                options.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = st.workConvert == mode,
                        onClick = { onWorkConvert(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(stringResource(label), fontSize = 13.sp) }
                }
            }
            ToggleRow(stringResource(R.string.reader_taiwan_phrases), stringResource(R.string.reader_taiwan_phrases_desc), s.taiwanPhrases, colors) { v ->
                scope.launch { container.readerPrefs.update { it.copy(taiwanPhrases = v) }; onGlobalConvertChanged() }
            }

            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = colors.secondary.copy(alpha = 0.2f))
            ToggleRow(stringResource(R.string.reader_indent), null, s.indent, colors) { v -> update { it.copy(indent = v) } }
            ToggleRow(stringResource(R.string.reader_justify), null, s.justify, colors) { v -> update { it.copy(justify = v) } }
            ToggleRow(stringResource(R.string.reader_show_notes), null, s.showNotes, colors) { v -> update { it.copy(showNotes = v) } }
            ToggleRow(stringResource(R.string.reader_tap_to_turn), stringResource(R.string.reader_tap_to_turn_desc), s.tapToTurn, colors) { v -> update { it.copy(tapToTurn = v) } }
            ToggleRow(stringResource(R.string.reader_page_animation), stringResource(R.string.reader_page_animation_desc), s.pageAnimation, colors) { v -> update { it.copy(pageAnimation = v) } }
            ToggleRow(stringResource(R.string.reader_volume_keys), null, s.volumeKeys, colors) { v -> update { it.copy(volumeKeys = v) } }
            ToggleRow(stringResource(R.string.reader_keep_screen_on), null, s.keepScreenOn, colors) { v -> update { it.copy(keepScreenOn = v) } }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Label(text: String, colors: ReaderColors, modifier: Modifier = Modifier) {
    Text(text, color = colors.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = modifier.padding(top = 10.dp, bottom = 2.dp))
}

@Composable
private fun SliderRow(label: String, value: String, colors: ReaderColors, slider: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Label(label, colors, Modifier.weight(1f))
        Text(value, color = colors.text, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
    }
    slider()
}

@Composable
private fun ToggleRow(title: String, desc: String?, checked: Boolean, colors: ReaderColors, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text, fontSize = 15.sp)
            if (desc != null) Text(desc, color = colors.secondary, fontSize = 12.sp)
        }
        Switch(checked, onChange)
    }
}
