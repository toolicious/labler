package io.github.toolicious.labler.ui.editor

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import io.github.toolicious.labler.App
import io.github.toolicious.labler.R
import io.github.toolicious.labler.data.Glyph
import io.github.toolicious.labler.data.GlyphSearch
import io.github.toolicious.labler.data.IconCatalog
import io.github.toolicious.labler.model.IconFonts
import io.github.toolicious.labler.ui.components.ClearButton
import io.github.toolicious.labler.ui.components.iconFontFamily
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

// Tab identities, as stored in the settings. These numbers say which tab, not where it sits:
// icons arrived last and were then put in front, and renumbering would have silently moved
// everyone's remembered tab to a different one. TAB_ORDER alone decides what goes where.
private const val TAB_SYMBOLS = 0
private const val TAB_EMOJIS = 1
private const val TAB_ICONS = 2
private val TAB_ORDER = listOf(TAB_ICONS, TAB_EMOJIS, TAB_SYMBOLS)

/**
 * How tall a tab is. All three state it, so switching between them cannot move the sheet by a
 * pixel. In the icon tab the grid takes whatever the search field and the recent row leave over
 * rather than a number of its own, which also holds up when a larger system font size makes the
 * search field grow.
 */
private val TAB_CONTENT_HEIGHT = 520.dp

/**
 * Cell geometry of the icon tab. The recent row lays its cells out at the width the grid derives
 * for a column, so the two read as one set of columns. They can differ by a pixel at the right
 * edge, because the grid hands its rounding remainder to the leftmost columns.
 */
private val ICON_CELL_WIDTH = 46.dp
private val ICON_CELL_HEIGHT = 44.dp

/** Icon names are machine readable (add_a_photo); this is the form to put in front of a person. */
private fun readableIconName(name: String) = name.replace('_', ' ')

private fun tabTitle(id: Int) = when (id) {
    TAB_ICONS -> R.string.tab_icons
    TAB_EMOJIS -> R.string.tab_emojis
    else -> R.string.tab_symbols
}

/**
 * Most recently used tab, see the identities above. Process-wide cache, loaded from the settings
 * at app start and saved back there on change, so that the choice is preserved across restarts.
 */
internal var lastSymbolTab = TAB_ICONS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymbolPickerSheet(
    /** Picked glyph, the icon font it needs (null for plain Unicode), and whether it is an emoji. */
    onPick: (String, String?, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as App).container }
    val settings = container.settings
    val recentIcons by settings.recentIcons.collectAsState(initial = emptyList())
    var tab by remember { mutableIntStateOf(lastSymbolTab) }
    // One size only. A partially expanded sheet still lays its content out at full height and
    // simply clips what sticks out below the screen, so the bottom of a grid was unreachable no
    // matter how far it was scrolled. The single state also means the sheet cannot come back from
    // the keyboard a different size than it went in.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // The content area does NOT move the dialog, otherwise it would collide with
        // scrolling. Only the handle at the top pulls it down to close.
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
                                if (dy > 24f) scope.launch {
                                    sheetState.hide()
                                    onDismiss()
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
            TabRow(selectedTabIndex = TAB_ORDER.indexOf(tab).coerceAtLeast(0)) {
                TAB_ORDER.forEach { id ->
                    Tab(
                        selected = tab == id,
                        onClick = {
                            tab = id
                            lastSymbolTab = id
                            container.applicationScope.launch { settings.saveLastSymbolTab(id) }
                        },
                        text = { Text(stringResource(tabTitle(id))) }
                    )
                }
            }

            if (tab == TAB_SYMBOLS) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 46.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TAB_CONTENT_HEIGHT)
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
                                    .clickable { onPick(glyph, null, false) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(glyph, fontSize = 24.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            } else if (tab == TAB_EMOJIS) {
                val currentOnPick by rememberUpdatedState(onPick)
                AndroidView(
                    factory = { ctx ->
                        EmojiPickerView(ctx).apply {
                            setOnEmojiPickedListener { currentOnPick(it.emoji, null, true) }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TAB_CONTENT_HEIGHT)
                )
            } else {
                IconTab(
                    recent = recentIcons,
                    // Written on the application scope: picking closes the sheet, and a scope tied
                    // to this composable would be cancelled before the store is through.
                    onForget = { entry ->
                        container.applicationScope.launch { settings.removeRecentIcon(entry.name) }
                        // The icon name, not the glyph: a toast is drawn in the system font, where
                        // a private use codepoint out of the icon font would come out as an empty
                        // box. The name is what identifies it anyway, and it is what the search
                        // matches on.
                        Toast.makeText(
                            context,
                            context.getString(R.string.icon_recent_removed, readableIconName(entry.name)),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onPick = { entry ->
                        container.applicationScope.launch { settings.addRecentIcon(entry.name) }
                        onPick(entry.glyph, entry.fontKey, false)
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * The bundled icon font, searched by name and keyword. The search field lives here for now; once
 * the emoji and the symbols above join the same index it moves out of this tab and above the tabs,
 * which is why it does its work through the source-agnostic GlyphSearch rather than on icons.
 */
@Composable
private fun IconTab(recent: List<String>, onForget: (Glyph) -> Unit, onPick: (Glyph) -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<Glyph>?>(null) }
    LaunchedEffect(Unit) { entries = IconCatalog.load(context) }
    var query by remember { mutableStateOf("") }
    val shown = remember(entries, query) { entries?.let { GlyphSearch.match(it, query) }.orEmpty() }
    val recentGlyphs = remember(entries, recent) {
        val byName = entries?.associateBy { it.name }.orEmpty()
        recent.mapNotNull { byName[it] }
    }
    val family = iconFontFamily(IconFonts.MATERIAL)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth / ICON_CELL_WIDTH).toInt().coerceAtLeast(1)
        val cell = maxWidth / columns
        Column(
            Modifier
                .fillMaxWidth()
                .height(TAB_CONTENT_HEIGHT)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                placeholder = { Text(stringResource(R.string.icon_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = { if (query.isNotEmpty()) ClearButton { query = "" } },
                singleLine = true
            )
            // As many of the recent ones as fit on one line, and only while nothing is being
            // searched for: during a search they answer a question nobody asked and sit there
            // looking like results that do not match. The row keeps its height while empty, so
            // picking a first icon does not move anything; the grid takes the space back when the
            // row goes, which leaves the sheet the same height either way.
            if (query.isBlank()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(ICON_CELL_HEIGHT)
                ) {
                    recentGlyphs.take(columns).forEach { entry ->
                        IconCell(
                            entry = entry,
                            family = family,
                            modifier = Modifier.width(cell),
                            onClick = { onPick(entry) },
                            onLongClick = { onForget(entry) },
                            longClickLabel = stringResource(R.string.cd_delete),
                        )
                    }
                }
                HorizontalDivider()
            }
            if (entries != null && shown.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.home_no_results), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.icon_search_english),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(shown, key = { it.name }) { entry ->
                        IconCell(
                            entry = entry,
                            family = family,
                            // The grid already fixes the width of a cell; stating one here would
                            // only be coerced back to the same value.
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onPick(entry) },
                            // Nothing else says what an icon is called, and the name is what the
                            // search runs on, so a long press is where to find it out.
                            onLongClick = { Toast.makeText(context, readableIconName(entry.name), Toast.LENGTH_SHORT).show() },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One tappable icon. The long press names it in the grid and drops it out of the recent row, hence
 * [longClickLabel], which is what a screen reader announces for that gesture.
 */
@Composable
private fun IconCell(
    entry: Glyph,
    family: FontFamily,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    longClickLabel: String? = null,
) {
    val name = readableIconName(entry.name)
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .height(ICON_CELL_HEIGHT)
            // Inset so the ripple of one cell does not run into the next.
            .padding(2.dp)
            .combinedClickable(
                onClick = onClick,
                // Fired here rather than left to the built-in one, so there is exactly one pulse
                // and it lands the moment the press registers, before the toast appears.
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onLongClickLabel = longClickLabel,
                hapticFeedbackEnabled = false,
            )
            // The glyph is a private use codepoint, which a screen reader can make nothing of.
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center
    ) {
        Text(
            entry.glyph,
            fontFamily = family,
            fontSize = 26.sp,
            // A private use codepoint reads as nothing, so the description on the cell stands alone.
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
