package io.github.toolicious.labler.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import io.github.toolicious.labler.App
import io.github.toolicious.labler.R
import kotlinx.coroutines.launch

private data class GlyphCategory(val titleRes: Int, val glyphs: List<String>)

// U+FE0E forces the text presentation so that symbols are not automatically
// turned into colored emojis. U+FE0F conversely forces the emoji presentation.
private val TEXT: String = Char(0xFE0E).toString()

private fun sym(vararg g: String) = g.map { it + TEXT }

private val SYMBOL_CATEGORIES = listOf(
    GlyphCategory(R.string.cat_arrows, sym(
        "→", "←", "↑", "↓", "↔", "↕", "↖", "↗", "↘", "↙", "⇄", "⇅",
        "⟶", "⟵", "➜", "➔", "▶", "◀", "»", "«", "⇧", "⇩", "↩", "↪",
    )),
    GlyphCategory(R.string.cat_shapes, sym(
        "●", "○", "◉", "■", "□", "▪", "▫", "▲", "△", "▼", "▽", "◆",
        "◇", "⬤", "⬛", "⬜", "⬟", "⬢", "▬", "▮", "◢", "◣", "◤", "◥",
    )),
    GlyphCategory(R.string.cat_stars, sym(
        "★", "☆", "✦", "✧", "✩", "✪", "✫", "✬", "✭", "✮", "✯", "❂",
        "❇", "❈", "❀", "✿", "❁", "✽", "❉", "❋",
    )),
    GlyphCategory(R.string.cat_signs, sym(
        "✓", "✔", "✗", "✘", "☑", "☒", "⚠", "⛔", "❗", "❓", "ℹ", "⚡",
        "☢", "☣", "♻", "⚑", "⚐", "✝", "☓", "✚", "⌘", "⏻",
    )),
    GlyphCategory(R.string.cat_tech, sym(
        "✉", "☎", "✆", "✂", "✎", "✏", "✒", "⌨", "⏏", "✈", "⚙", "⌂",
        "⌚", "⏱", "☕", "⚖", "⌛", "⏮", "⏪", "⏯", "⏩", "⏭",
    )),
    GlyphCategory(R.string.cat_weather, sym(
        "☀", "☁", "☂", "☃", "❄", "☔", "☾", "☽", "☘", "♣", "♠", "♥", "♦",
    )),
    GlyphCategory(R.string.cat_math, sym(
        "±", "×", "÷", "∞", "≈", "≠", "≤", "≥", "√", "∑", "∏", "∆",
        "π", "µ", "Ω", "°", "′", "″", "‰", "∅", "½", "¼", "¾",
    )),
    GlyphCategory(R.string.cat_misc, sym(
        "♫", "♪", "♬", "§", "¶", "†", "‡", "•", "‣", "◦", "€", "$",
        "£", "¥", "¢", "©", "®", "™", "☮", "☯", "№", "℮", "✰",
    )),
)

/**
 * Most recently used tab (0 = symbols, 1 = emojis). Process-wide cache, loaded
 * from the settings at app start and saved back there on change, so that the
 * choice is preserved across restarts.
 */
internal var lastSymbolTab = 0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymbolPickerSheet(
    onPick: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember(context) { (context.applicationContext as App).container.settings }
    var tab by remember { mutableIntStateOf(lastSymbolTab) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // The content area does NOT move the dialog, otherwise it would collide with
        // scrolling. Only the handle at the top pulls it up (larger) or down (close).
        sheetGesturesEnabled = false,
        dragHandle = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        var dy = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dy = 0f },
                            onVerticalDrag = { _, d -> dy += d },
                            onDragEnd = {
                                when {
                                    dy < -24f -> scope.launch { sheetState.expand() }
                                    dy > 24f -> scope.launch {
                                        if (sheetState.currentValue == SheetValue.Expanded) {
                                            sheetState.partialExpand()
                                        } else {
                                            sheetState.hide()
                                            onDismiss()
                                        }
                                    }
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                BottomSheetDefaults.DragHandle()
            }
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0; lastSymbolTab = 0; scope.launch { settings.saveLastSymbolTab(0) } },
                    text = { Text(stringResource(R.string.tab_symbols)) }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1; lastSymbolTab = 1; scope.launch { settings.saveLastSymbolTab(1) } },
                    text = { Text(stringResource(R.string.tab_emojis)) }
                )
            }

            if (tab == 0) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 46.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                ) {
                    SYMBOL_CATEGORIES.forEach { category ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(category.titleRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(category.glyphs) { glyph ->
                            Box(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .height(44.dp)
                                    .fillMaxWidth()
                                    .clickable { onPick(glyph, false) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(glyph, fontSize = 24.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            } else {
                val currentOnPick by rememberUpdatedState(onPick)
                AndroidView(
                    factory = { ctx ->
                        EmojiPickerView(ctx).apply {
                            setOnEmojiPickedListener { currentOnPick(it.emoji, true) }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
