package io.github.toolicious.labler.ui.home

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.toolicious.labler.R
import io.github.toolicious.labler.data.TemplateSort
import io.github.toolicious.labler.data.TemplateViewMode
import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.model.LabelTemplate
import io.github.toolicious.labler.model.LengthMode
import io.github.toolicious.labler.printer.MediaType
import io.github.toolicious.labler.printer.PrinterFamily
import kotlin.math.roundToInt
import io.github.toolicious.labler.render.FontRegistry
import io.github.toolicious.labler.render.LabelRenderer
import io.github.toolicious.labler.ui.components.ClearButton
import io.github.toolicious.labler.ui.components.PrinterStatusChip
import io.github.toolicious.labler.ui.components.rememberBlePermissionRunner
import io.github.toolicious.labler.ui.components.rememberBlePermissionState
import io.github.toolicious.labler.ui.info.InfoDialog
import io.github.toolicious.labler.ui.print.TemplatePrintSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenTemplate: (String) -> Unit,
    onOpenHistory: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val templates by vm.templates.collectAsState()
    val query by vm.query.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val viewMode = prefs.viewMode
    val filter by vm.filter.collectAsState()
    val facets by vm.facets.collectAsState()
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    val printerState by vm.printerState.collectAsState()
    val savedPrinter by vm.savedPrinter.collectAsState()
    val blePermission = rememberBlePermissionState()
    // Same gate the editor's print button uses: open the sheet only once Bluetooth is granted.
    val withBlePermissions = rememberBlePermissionRunner()
    var showNewDialog by rememberSaveable { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<LabelTemplate?>(null) }
    var exportTarget by remember { mutableStateOf<LabelTemplate?>(null) }
    var deleteTarget by remember { mutableStateOf<LabelTemplate?>(null) }
    var printTarget by remember { mutableStateOf<LabelTemplate?>(null) }
    // Default name locale-safe from the UI (Compose follows the current app language).
    val defaultLabelName = stringResource(R.string.default_label_name)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val target = exportTarget
        exportTarget = null
        if (uri != null && target != null) {
            vm.exportTo(uri, target) { error ->
                val msg = error?.let { context.getString(R.string.toast_export_failed, it) }
                    ?: context.getString(R.string.toast_export_ok)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.importFrom(uri, defaultLabelName) { error, newId ->
                if (error != null) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_import_failed, error),
                        Toast.LENGTH_LONG
                    ).show()
                } else if (newId != null) {
                    onOpenTemplate(newId)
                }
            }
        }
    }

    var showInfoDialog by rememberSaveable { mutableStateOf(false) }
    // Hoisted out of the branch that uses them, so switching the view mode and back returns to
    // where the user was instead of jumping to the top.
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    // Set when the star is tapped, cleared once the re-sorted list has been followed.
    var favoriteScrollTarget by remember { mutableStateOf<String?>(null) }

    // Favoriting re-sorts the overview, which can carry the label right off the screen. The effect
    // waits for the new order to arrive, lets it lay out, then follows the label just far enough
    // to bring it back into view.
    LaunchedEffect(templates) {
        val id = favoriteScrollTarget ?: return@LaunchedEffect
        favoriteScrollTarget = null
        val index = templates.indexOfFirst { it.id == id }
        if (index < 0) return@LaunchedEffect
        withFrameNanos { }
        when (viewMode) {
            TemplateViewMode.GRID -> gridState.ensureItemVisible(index)
            TemplateViewMode.LIST -> listState.ensureItemVisible(index)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorResource(R.color.ic_launcher_background),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painterResource(R.drawable.ic_logo_color),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .width(24.dp)
                                .height(30.dp)
                        )
                        // The gap survives even when the chip has taken everything else, so the
                        // two never end up touching on a narrow screen.
                        Text(stringResource(R.string.app_name), Modifier.padding(end = 8.dp))
                        Spacer(Modifier.weight(1f))
                        // Tapping opens the printer settings, or requests the Bluetooth permission when
                        // a printer is remembered but the permission is missing (so it can never connect).
                        val permMissing = !blePermission.granted && savedPrinter != null
                        PrinterStatusChip(
                            printerState,
                            permissionMissing = permMissing,
                            onClick = if (permMissing) blePermission.request else onOpenSettings,
                        )
                        Spacer(Modifier.weight(1f))
                    }
                },
                actions = {
                    // The print history lives in the toolbar under the search field now, next to
                    // the other things that act on the list.
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            painterResource(R.drawable.ic_info),
                            contentDescription = stringResource(R.string.cd_info)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_new_label))
            }
        }
    ) { padding ->
        // The margin sits on the pieces rather than on the column, so the list can run edge to
        // edge and put it back through its own row padding, which lines its content up with the
        // search field while the row itself, and its ripple, still spans the full width.
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Spacer(Modifier.height(12.dp))
            // Search on the left, everything that acts on the list on the right.
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.home_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = { if (query.isNotEmpty()) ClearButton { vm.setQuery("") } },
                    singleLine = true
                )
                SortButton(
                    sort = prefs.sort,
                    ascending = prefs.ascending,
                    onSelect = vm::selectSort,
                    onReset = vm::resetSort,
                    onOpenHistory = onOpenHistory,
                )
                FilterButton(
                    activeCount = filter.activeCount,
                    // Nothing to choose from means nothing to open.
                    enabled = !facets.isEmpty,
                    onClick = { showFilterSheet = true },
                    onClearAll = vm::clearFilter,
                )
                ViewModeButton(current = viewMode, onSelect = vm::setViewMode)
            }
            Spacer(Modifier.height(12.dp))

            if (templates.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (query.isBlank()) stringResource(R.string.home_empty)
                        else stringResource(R.string.home_no_results),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                // Written once and handed to whichever layout is showing, so the two cannot drift
                // apart.
                val renderItem: @Composable (LabelTemplate) -> Unit = { template ->
                    val copyName = stringResource(R.string.duplicate_name, template.name)
                    TemplateItem(
                        template = template,
                        mode = viewMode,
                        onClick = { onOpenTemplate(template.id) },
                        onPrint = { withBlePermissions { printTarget = template } },
                        onToggleFavorite = {
                            favoriteScrollTarget = template.id
                            vm.toggleFavorite(template)
                        },
                        onEdit = { editTarget = template },
                        onDuplicate = { vm.duplicate(template.id, copyName) },
                        onDelete = { deleteTarget = template },
                        onExport = {
                            exportTarget = template
                            exportLauncher.launch("${template.name}.labler.json")
                        },
                    )
                }
                // The bottom padding is what keeps the last item clear of the floating button.
                //
                // Deliberately without animateItem(): the only thing that re-sorts the overview is
                // the favorite star, which is exactly when the view scrolls after the label. Item
                // animation and scroll animation ran against each other and left the layout on a
                // stale anchor, so the next touch made unrelated rows jump into place.
                when (viewMode) {
                    TemplateViewMode.GRID -> LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    ) {
                        items(templates, key = { it.id }) { template ->
                            renderItem(template)
                        }
                    }
                    TemplateViewMode.LIST -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 88.dp),
                    ) {
                        items(templates, key = { it.id }) { template ->
                            renderItem(template)
                        }
                    }
                }
            }
        }
    }

    if (showInfoDialog) {
        InfoDialog(onDismiss = { showInfoDialog = false })
    }

    if (showFilterSheet) {
        FilterSheet(
            facets = facets,
            filter = filter,
            // The list is already narrowed down, so its length is the number of hits.
            matches = templates.size,
            onChange = vm::setFilter,
            onReset = vm::clearFilter,
            onDismiss = { showFilterSheet = false },
        )
    }

    printTarget?.let { target ->
        TemplatePrintSheet(
            template = target,
            onDismiss = { printTarget = null },
            onOpenSettings = onOpenSettings,
        )
    }

    if (showNewDialog) {
        LabelDialog(
            title = stringResource(R.string.dialog_new_title),
            initialName = "",
            initialSpec = LabelSpec.forFamily(savedPrinter?.family ?: PrinterFamily.DEFAULT),
            onDismiss = { showNewDialog = false },
            onConfirm = { name, spec ->
                showNewDialog = false
                vm.create(name, spec, defaultLabelName, onOpenTemplate)
            },
            onImport = {
                showNewDialog = false
                importLauncher.launch(arrayOf("application/json"))
            },
            autofocusName = true
        )
    }

    editTarget?.let { target ->
        LabelDialog(
            title = stringResource(R.string.dialog_edit_title),
            initialName = target.name,
            initialSpec = target.spec,
            onDismiss = { editTarget = null },
            onConfirm = { name, spec ->
                vm.updateMeta(target.id, name, spec)
                editTarget = null
            },
            onImport = null,
            currentLengthMm = LabelRenderer.effectiveLengthMm(target.spec, target.elements),
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message, target.name)) },
            confirmButton = {
                Button(
                    onClick = { vm.delete(target.id); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.menu_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

/**
 * Scrolls only as far as it takes to bring the item at [index] fully into view, and not at all
 * when it already is. A negative offset in the second case lands the item's bottom edge on the
 * viewport's, so the list moves the least it can.
 */
private suspend fun LazyListState.ensureItemVisible(index: Int) {
    val info = layoutInfo
    val viewport = info.viewportEndOffset - info.viewportStartOffset
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    when {
        item == null || item.offset < info.viewportStartOffset -> animateScrollToItem(index)
        item.offset + item.size > info.viewportEndOffset ->
            animateScrollToItem(index, item.size - viewport)
    }
}

/** [ensureItemVisible] for the grid, whose items carry a two-dimensional offset and size. */
private suspend fun LazyGridState.ensureItemVisible(index: Int) {
    val info = layoutInfo
    val viewport = info.viewportEndOffset - info.viewportStartOffset
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    when {
        item == null || item.offset.y < info.viewportStartOffset -> animateScrollToItem(index)
        item.offset.y + item.size.height > info.viewportEndOffset ->
            animateScrollToItem(index, item.size.height - viewport)
    }
}

/** One label, drawn the way the overview is currently set to draw it. */
@Composable
private fun TemplateItem(
    template: LabelTemplate,
    mode: TemplateViewMode,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onPrint: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    when (mode) {
        TemplateViewMode.GRID -> TemplateCard(
            template, modifier, onClick, onPrint, onToggleFavorite,
            onEdit, onDuplicate, onDelete, onExport,
        )
        TemplateViewMode.LIST -> TemplateRow(
            template, modifier, onClick, onPrint, onToggleFavorite,
            onEdit, onDuplicate, onDelete, onExport,
        )
    }
}

/**
 * Length the label reaches right now, in dots. An auto-length label is as long as its content and
 * cannot use the stored length. The font revision is a key as well, so the value follows a custom
 * font becoming available or being removed.
 */
@Composable
private fun rememberLabelLengthPx(template: LabelTemplate): Int =
    remember(template.id, template.updatedAt, FontRegistry.revision) {
        LabelRenderer.effectiveLengthPx(template.spec, template.elements)
    }

/**
 * Preview of a label, for both the grid and the list. The caller sizes it, because the two want
 * different things: the card gives it the label's own aspect ratio, the row a fixed box that every
 * ratio has to fit into.
 */
@Composable
private fun LabelThumbnail(
    template: LabelTemplate,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillBounds,
) {
    val bitmap = remember(template.id, template.updatedAt, FontRegistry.revision) {
        LabelRenderer.render(template.spec, template.elements).asImageBitmap()
    }
    // Fixed size (die-cut label) = rounded corners, continuous = hard corners.
    val labelShape = if (template.spec.media == MediaType.DIE_CUT) {
        RoundedCornerShape(6.dp)
    } else {
        RectangleShape
    }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier
            .clip(labelShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, labelShape),
        contentScale = contentScale,
    )
}

/** Dimensions of a label, in the variable form where the length follows the content. */
@Composable
private fun templateSizeText(template: LabelTemplate, lengthPx: Int): String =
    if (template.spec.lengthIsAuto) {
        // Reuses the length already measured for the preview.
        stringResource(
            R.string.template_size_auto,
            template.spec.tapeWidthMm,
            template.spec.geometry.dotsToMm(lengthPx),
        )
    } else {
        stringResource(R.string.template_size, template.spec.tapeWidthMm, template.spec.lengthMm)
    }

/** The five things that can be done to a label, the same in the grid and in the list. */
@Composable
private fun TemplateMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPrint: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // Accent-colored and first, because printing is the main action here;
        // the same entry the long press on the item triggers.
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_print)) },
            colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.primary),
            onClick = { onDismiss(); onPrint() })
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_edit)) },
            onClick = { onDismiss(); onEdit() })
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_duplicate)) },
            onClick = { onDismiss(); onDuplicate() })
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_export)) },
            onClick = { onDismiss(); onExport() })
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_delete)) },
            onClick = { onDismiss(); onDelete() })
    }
}

/** The favorite star, which looks and behaves the same in both layouts. */
@Composable
private fun FavoriteButton(favorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            Icons.Default.Star,
            contentDescription = stringResource(R.string.cd_favorite),
            tint = if (favorite) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun TemplateCard(
    template: LabelTemplate,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onPrint: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        // combinedClickable replaces the clickable ElevatedCard overload, which knows no long press.
        // It sits on the content rather than on the card's own modifier: the card surface already
        // clips its content, so the ripple stays inside the rounded corners without an extra clip
        // that would cut off the drop shadow. Role and long-click label restore the semantics the
        // clickable overload provided; the long-press haptic comes from combinedClickable itself.
        Column(
            Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onPrint,
                    onLongClickLabel = stringResource(R.string.action_print),
                    role = Role.Button,
                )
                .padding(10.dp)
        ) {
            val lengthPx = rememberLabelLengthPx(template)
            LabelThumbnail(
                template,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(lengthPx.toFloat() / template.spec.printHeightPx),
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    template.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.width(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_menu))
                    }
                    TemplateMenu(
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        onPrint = onPrint,
                        onEdit = onEdit,
                        onDuplicate = onDuplicate,
                        onExport = onExport,
                        onDelete = onDelete,
                    )
                }
            }
            // Dimensions on the left, favorite star in the bottom-right corner (there is free space there).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    templateSizeText(template, lengthPx),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                FavoriteButton(template.favorite, onToggleFavorite, Modifier.width(32.dp))
            }
        }
    }
}

/**
 * One label as a list row: star, preview, name over dimensions, menu.
 *
 * The preview sits in a fixed box instead of at the label's own aspect ratio. A 12x100 mm label is
 * 8:1 and at row height would come out wider than the screen, so every preview is fitted into the
 * same slot and the rows line up.
 */
@Composable
private fun TemplateRow(
    template: LabelTemplate,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onPrint: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val lengthPx = rememberLabelLengthPx(template)

    ListItem(
        // Star and menu are buttons of their own and take their taps first, so a tap anywhere else
        // on the row opens the label and a long press prints it, exactly as on the card.
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onPrint,
            onLongClickLabel = stringResource(R.string.action_print),
            role = Role.Button,
        ),
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FavoriteButton(template.favorite, onToggleFavorite, Modifier.size(40.dp))
                Spacer(Modifier.width(4.dp))
                LabelThumbnail(
                    template,
                    Modifier.size(width = 56.dp, height = 36.dp),
                    ContentScale.Fit,
                )
            }
        },
        headlineContent = {
            Text(template.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                templateSizeText(template, lengthPx),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_menu))
                }
                TemplateMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    onPrint = onPrint,
                    onEdit = onEdit,
                    onDuplicate = onDuplicate,
                    onExport = onExport,
                    onDelete = onDelete,
                )
            }
        },
    )
}

/**
 * A button of the toolbar beside the search field. Hand-built rather than an [IconButton], because
 * two of these carry a long press to reset what they set, and IconButton knows only the short one.
 */
@Composable
private fun ToolbarIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
                role = Role.Button,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter,
            contentDescription = contentDescription,
            // What a disabled IconButton would fade its content to.
            tint = LocalContentColor.current.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

/**
 * Picks what the overview is ordered by, and holds the way to the print history below a rule. One
 * entry per criterion; picking the one that is already active turns it around, and a long press on
 * the button puts the order back to the one the app starts with. The icon carries the direction,
 * because in a row of bare icons nothing else would show it.
 */
@Composable
private fun SortButton(
    sort: TemplateSort,
    ascending: Boolean,
    onSelect: (TemplateSort) -> Unit,
    onReset: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolbarIconButton(
            painter = painterResource(
                if (ascending) R.drawable.ic_sort_asc else R.drawable.ic_sort_desc
            ),
            contentDescription = stringResource(R.string.cd_sort_options),
            onClick = { open = true },
            onLongClick = onReset,
            onLongClickLabel = stringResource(R.string.cd_sort_reset),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TemplateSort.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(stringResource(sortLabel(entry))) },
                    leadingIcon = { Icon(painterResource(sortIcon(entry)), contentDescription = null) },
                    // Only the active criterion carries an arrow, and it says which way it runs.
                    trailingIcon = {
                        if (entry == sort) {
                            Icon(
                                if (ascending) Icons.Default.KeyboardArrowUp
                                else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        }
                    },
                    onClick = { open = false; onSelect(entry) },
                )
            }
            // The history is not an order, hence the rule; it sits here because it belongs to the
            // list and the row above has no room left for it.
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_history)) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_history), contentDescription = null)
                },
                onClick = { open = false; onOpenHistory() },
            )
        }
    }
}

/**
 * Opens the filter sheet, and drops every filter on a long press. The badge is the only place an
 * active filter shows in a row of icons.
 */
@Composable
private fun FilterButton(
    activeCount: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    onClearAll: () -> Unit,
) {
    BadgedBox(badge = { if (activeCount > 0) Badge { Text(activeCount.toString()) } }) {
        ToolbarIconButton(
            painter = painterResource(R.drawable.ic_filter),
            contentDescription = stringResource(R.string.cd_filter_options),
            onClick = onClick,
            enabled = enabled,
            onLongClick = onClearAll,
            onLongClickLabel = stringResource(R.string.cd_filter_reset),
        )
    }
}

/** Picks how the overview lays its labels out. The button carries the icon of the active mode. */
@Composable
private fun ViewModeButton(current: TemplateViewMode, onSelect: (TemplateViewMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolbarIconButton(
            painter = painterResource(viewModeIcon(current)),
            contentDescription = stringResource(R.string.cd_view_options),
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TemplateViewMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(viewModeLabel(mode))) },
                    leadingIcon = {
                        Icon(painterResource(viewModeIcon(mode)), contentDescription = null)
                    },
                    trailingIcon = {
                        if (mode == current) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    },
                    onClick = { open = false; onSelect(mode) },
                )
            }
        }
    }
}

/**
 * Narrows the overview down. A sheet rather than a menu, because depending on what is stored a
 * dozen chips can come together here. Every group offers only what actually occurs, and a group
 * with a single option is left out because it would narrow nothing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    facets: TemplateFacets,
    filter: TemplateFilter,
    matches: Int,
    onChange: (TemplateFilter) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            if (facets.media.size > 1) {
                FilterGroup(R.string.filter_media) {
                    facets.media.forEach { (media, count) ->
                        FilterSheetChip(
                            selected = media in filter.media,
                            label = stringResource(
                                if (media == MediaType.DIE_CUT) R.string.media_die_cut
                                else R.string.media_continuous
                            ),
                            count = count,
                            onClick = { onChange(filter.copy(media = filter.media.toggle(media))) },
                        )
                    }
                }
            }

            if (facets.tapeWidthsMm.size > 1) {
                FilterGroup(R.string.filter_width) {
                    facets.tapeWidthsMm.forEach { (width, count) ->
                        FilterSheetChip(
                            selected = width in filter.tapeWidthsMm,
                            label = stringResource(R.string.filter_width_mm, width),
                            count = count,
                            onClick = {
                                onChange(filter.copy(tapeWidthsMm = filter.tapeWidthsMm.toggle(width)))
                            },
                        )
                    }
                }
            }

            if (facets.lengths.size > 1) {
                FilterGroup(R.string.filter_length) {
                    facets.lengths.forEach { (bucket, count) ->
                        FilterSheetChip(
                            selected = bucket in filter.lengths,
                            label = lengthLabel(bucket),
                            count = count,
                            onClick = {
                                onChange(filter.copy(lengths = filter.lengths.toggle(bucket)))
                            },
                        )
                    }
                }
            }

            if (facets.families.size > 1) {
                FilterGroup(R.string.filter_printer) {
                    facets.families.forEach { (family, count) ->
                        FilterSheetChip(
                            selected = family in filter.families,
                            label = stringResource(familyLabel(family)),
                            count = count,
                            onClick = {
                                onChange(filter.copy(families = filter.families.toggle(family)))
                            },
                        )
                    }
                }
            }

            if (facets.hasFavorites || facets.hasCode) {
                FilterGroup(R.string.filter_other) {
                    if (facets.hasFavorites) {
                        FilterSheetChip(
                            selected = filter.favoritesOnly,
                            label = stringResource(R.string.filter_favorites_only),
                            count = facets.favoritesCount,
                            onClick = { onChange(filter.copy(favoritesOnly = !filter.favoritesOnly)) },
                        )
                    }
                    if (facets.hasCode) {
                        FilterSheetChip(
                            selected = filter.withCode,
                            label = stringResource(R.string.filter_has_code),
                            count = facets.codeCount,
                            onClick = { onChange(filter.copy(withCode = !filter.withCode)) },
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.filter_matches, matches),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onReset, enabled = !filter.isEmpty) {
                    Text(stringResource(R.string.filter_reset))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Heading plus a wrapping row of chips, the shape every group in the filter sheet has. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(titleRes: Int, content: @Composable FlowRowScope.() -> Unit) {
    Text(stringResource(titleRes), style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
    Spacer(Modifier.height(12.dp))
}

/** A chip with the number of labels it would leave behind it, so a dead end is visible before it. */
@Composable
private fun FilterSheetChip(selected: Boolean, label: String, count: Int, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                stringResource(R.string.filter_option_count, label, count),
                maxLines = 1,
                softWrap = false,
            )
        },
    )
}

/** Adds the value or takes it out again, which is what tapping a filter chip does. */
private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

@Composable
private fun lengthLabel(bucket: LengthBucket): String = when (bucket) {
    LengthBucket.UP_TO_25 -> stringResource(R.string.length_upto, bucket.range.last)
    LengthBucket.OVER_100 -> stringResource(R.string.length_over, bucket.range.first - 1)
    else -> stringResource(R.string.length_range, bucket.range.first, bucket.range.last)
}

private fun sortLabel(sort: TemplateSort) = when (sort) {
    TemplateSort.NAME -> R.string.sort_name
    TemplateSort.UPDATED -> R.string.sort_updated
    TemplateSort.PRINTS -> R.string.sort_prints
}

private fun sortIcon(sort: TemplateSort) = when (sort) {
    TemplateSort.NAME -> R.drawable.ic_sort_alpha
    TemplateSort.UPDATED -> R.drawable.ic_edit
    TemplateSort.PRINTS -> R.drawable.ic_print
}

private fun familyLabel(family: PrinterFamily) = when (family) {
    PrinterFamily.PHOMEMO -> R.string.family_phomemo
    PrinterFamily.DYMO -> R.string.family_dymo
}

private fun viewModeIcon(mode: TemplateViewMode) = when (mode) {
    TemplateViewMode.GRID -> R.drawable.ic_view_grid
    TemplateViewMode.LIST -> R.drawable.ic_view_list
}

private fun viewModeLabel(mode: TemplateViewMode) = when (mode) {
    TemplateViewMode.GRID -> R.string.menu_view_tiles
    TemplateViewMode.LIST -> R.string.menu_view_list
}

/**
 * Name and paper of a label, for both the new and the edit case.
 *
 * @param currentLengthMm length the label reaches right now, which the dialog cannot work out on
 *   its own because it never sees the elements. It pre-fills the length field when the user leaves
 *   the variable mode, so that picking another mode keeps the label as long as it already is.
 *   Null where there is nothing to measure yet, and then the minimum has to do.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LabelDialog(
    title: String,
    initialName: String,
    initialSpec: LabelSpec,
    onDismiss: () -> Unit,
    onConfirm: (String, LabelSpec) -> Unit,
    onImport: (() -> Unit)?,
    currentLengthMm: Int? = null,
    autofocusName: Boolean = false,
) {
    val geometry = initialSpec.geometry
    val isPresetSize = geometry.diecutPresets.any {
        it.first == initialSpec.tapeWidthMm && it.second == initialSpec.lengthMm
    }
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autofocusName) nameFocus.requestFocus() }
    // Two separate "custom" flags: die-cut picks a whole stock size, continuous only a width.
    var customSize by rememberSaveable(initialSpec) { mutableStateOf(!isPresetSize) }
    var customWidth by rememberSaveable(initialSpec) {
        mutableStateOf(initialSpec.tapeWidthMm !in geometry.tapeWidthsMm)
    }
    var widthText by rememberSaveable(initialSpec) { mutableStateOf(initialSpec.tapeWidthMm.toString()) }
    var lengthText by rememberSaveable(initialSpec) { mutableStateOf(initialSpec.lengthMm.toString()) }
    var marginText by rememberSaveable(initialSpec) {
        mutableStateOf(mmText(initialSpec.marginPx, geometry.headDotsPerMm))
    }
    val supportsDieCut = MediaType.DIE_CUT in initialSpec.supportedMedia
    var dieCut by rememberSaveable(initialSpec) {
        mutableStateOf(supportsDieCut && initialSpec.media == MediaType.DIE_CUT)
    }
    var lengthMode by rememberSaveable(initialSpec) { mutableStateOf(initialSpec.lengthMode) }
    // Picking a mode leaves the label exactly as long as it is now. In the variable mode the
    // number is a lower bound rather than the length, so leaving it hands the field the length the
    // label actually reaches; every other switch keeps the number that is already there. Going back
    // to variable therefore raises the minimum to the current length, which is the price of the
    // label not changing under the user, and one keystroke to undo.
    val selectLengthMode = { mode: LengthMode ->
        if (mode != lengthMode) {
            if (lengthMode == LengthMode.VARIABLE) currentLengthMm?.let { lengthText = it.toString() }
            lengthMode = mode
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // Wider than the platform default, which is narrow enough on a phone to break the size
        // and tape-width chips onto extra rows. Material3 still caps the dialog at 560 dp, so
        // this only widens it up to that, and the margin keeps it off the screen edges.
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocus),
                    trailingIcon = { if (name.isNotEmpty()) ClearButton { name = "" } }
                )
                Spacer(Modifier.height(12.dp))

                // The paper comes first, because it decides what the rest of the dialog means:
                // a die-cut label takes both dimensions from the stock in the printer, while
                // continuous tape only fixes the width and leaves the length to the design.
                // A printer that only knows one of the two is not asked.
                if (supportsDieCut) {
                    Text(stringResource(R.string.field_media), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = dieCut,
                            onClick = { dieCut = true },
                            label = { Text(stringResource(R.string.media_die_cut), maxLines = 1, softWrap = false) }
                        )
                        FilterChip(
                            selected = !dieCut,
                            onClick = {
                                // Switching over defaults to a variable length, because that is what
                                // continuous tape is for. An already-continuous label keeps its choice.
                                if (dieCut) {
                                    dieCut = false
                                    lengthMode = LengthMode.VARIABLE
                                }
                            },
                            label = { Text(stringResource(R.string.media_continuous), maxLines = 1, softWrap = false) }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (dieCut) {
                    // Stock sizes: these presets are commercially available die-cut labels, so
                    // they only make sense here.
                    Text(stringResource(R.string.size_hint), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        geometry.diecutPresets.forEach { (w, l) ->
                            FilterChip(
                                selected = !customSize && widthText == "$w" && lengthText == "$l",
                                onClick = {
                                    customSize = false
                                    widthText = "$w"
                                    lengthText = "$l"
                                },
                                label = { Text("${w}x$l", maxLines = 1, softWrap = false) }
                            )
                        }
                        FilterChip(
                            selected = customSize,
                            onClick = { customSize = true },
                            label = { Text(stringResource(R.string.preset_custom), maxLines = 1, softWrap = false) }
                        )
                    }
                    if (customSize) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MmField(
                                value = widthText,
                                onValueChange = { widthText = it },
                                label = stringResource(R.string.field_tape_mm),
                                maxDigits = 2,
                                range = geometry.minTapeMm..geometry.maxTapeMm,
                                modifier = Modifier.weight(1f),
                            )
                            MmField(
                                value = lengthText,
                                onValueChange = { lengthText = it },
                                label = stringResource(R.string.field_length_mm),
                                maxDigits = 3,
                                range = geometry.minLengthMm..geometry.maxLengthMm,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else {
                    // Continuous tape: the width is the cartridge, the length is a design choice.
                    Text(stringResource(R.string.field_tape_width), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        geometry.tapeWidthsMm.forEach { w ->
                            FilterChip(
                                selected = !customWidth && widthText == "$w",
                                onClick = {
                                    customWidth = false
                                    widthText = "$w"
                                },
                                label = { Text("$w mm", maxLines = 1, softWrap = false) }
                            )
                        }
                        FilterChip(
                            selected = customWidth,
                            onClick = { customWidth = true },
                            label = { Text(stringResource(R.string.preset_custom), maxLines = 1, softWrap = false) }
                        )
                    }
                    if (customWidth) {
                        Spacer(Modifier.height(8.dp))
                        MmField(
                            value = widthText,
                            onValueChange = { widthText = it },
                            label = stringResource(R.string.field_tape_mm),
                            maxDigits = 2,
                            range = geometry.minTapeMm..geometry.maxTapeMm,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.field_length), style = MaterialTheme.typography.bodySmall)
                    // Where the length comes from: the content, the edges dragged in the editor,
                    // or the number below. The dialog is wide enough for the three side by side.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = lengthMode == LengthMode.VARIABLE,
                            onClick = { selectLengthMode(LengthMode.VARIABLE) },
                            label = { Text(stringResource(R.string.length_variable), maxLines = 1, softWrap = false) }
                        )
                        FilterChip(
                            selected = lengthMode == LengthMode.MANUAL,
                            onClick = { selectLengthMode(LengthMode.MANUAL) },
                            label = { Text(stringResource(R.string.length_manual), maxLines = 1, softWrap = false) }
                        )
                        FilterChip(
                            selected = lengthMode == LengthMode.FIXED,
                            onClick = { selectLengthMode(LengthMode.FIXED) },
                            label = { Text(stringResource(R.string.length_fixed), maxLines = 1, softWrap = false) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // One length field in every mode; only its meaning changes, from exact to
                    // lower bound. In the manual mode it is the same number the edges carry, so
                    // typing one moves the trailing edge, which is the one the length belongs to.
                    //
                    // The margin sits beside it wherever the app places an edge itself, which is
                    // both ends of a variable label and the double tap fit of a manual one. A
                    // fixed length has no such edge and the field would do nothing there.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MmField(
                            value = lengthText,
                            onValueChange = { lengthText = it },
                            label = stringResource(
                                if (lengthMode == LengthMode.VARIABLE) R.string.field_min_length_mm
                                else R.string.field_length_mm
                            ),
                            maxDigits = 3,
                            range = geometry.minLengthMm..geometry.maxLengthMm,
                            modifier = Modifier.weight(1f),
                        )
                        if (lengthMode != LengthMode.FIXED) {
                            MmField(
                                value = marginText,
                                onValueChange = { marginText = it },
                                label = stringResource(R.string.field_margin_mm),
                                maxDigits = 5,
                                range = LabelSpec.MIN_MARGIN_MM..LabelSpec.MAX_MARGIN_MM,
                                fraction = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    // Spells out what the selected mode does, since the chip labels alone leave the
                    // difference between a minimum and a fixed length to guesswork. Always shown,
                    // for the same reason the field is.
                    Text(
                        stringResource(
                            when (lengthMode) {
                                LengthMode.VARIABLE -> R.string.length_hint_variable
                                LengthMode.MANUAL -> R.string.length_hint_manual
                                LengthMode.FIXED -> R.string.length_hint_fixed
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // Two of the three hints wrap at the width of the dialog and one does not,
                        // so the room for both lines is held whatever is selected. Otherwise the
                        // buttons below would jump on every switch, which is what the line being
                        // always present was meant to prevent in the first place.
                        minLines = 2,
                    )
                }
            }
        },
        confirmButton = {
            val submit = {
                val width = widthText.toIntOrNull()
                    ?.coerceIn(geometry.minTapeMm, geometry.maxTapeMm) ?: 12
                val length = lengthText.toIntOrNull()
                    ?.coerceIn(geometry.minLengthMm, geometry.maxLengthMm) ?: 40
                val media = if (dieCut) MediaType.DIE_CUT else MediaType.CONTINUOUS
                val mode = if (dieCut) LengthMode.FIXED else lengthMode
                val margin = mmPx(marginText, geometry.headDotsPerMm)
                // Edited from the spec that came in, so the label keeps the printer family it
                // was designed for. A die-cut label is always fixed, its length belongs to the stock.
                onConfirm(
                    name,
                    initialSpec.copy(
                        tapeWidthMm = width,
                        lengthMm = length,
                        media = media,
                        marginPx = margin,
                    ).withLengthMode(mode),
                )
            }
            if (onImport != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onImport) {
                        Icon(
                            painterResource(R.drawable.ic_import),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_import))
                    }
                    Button(onClick = submit) { Text(stringResource(R.string.action_create)) }
                }
            } else {
                Button(onClick = submit) { Text(stringResource(R.string.action_save)) }
            }
        }
    )
}

/** Numeric millimeter field of the label dialog (digits only, capped length). */
@Composable
private fun MmField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxDigits: Int,
    range: IntRange,
    modifier: Modifier = Modifier,
    fraction: Boolean = false,
) {
    val step = { by: Int ->
        val now = value.replace(',', '.').toFloatOrNull() ?: range.first.toFloat()
        val next = (now + by).coerceIn(range.first.toFloat(), range.last.toFloat())
        onValueChange(if (fraction) trimmed(next) else next.toInt().toString())
    }
    OutlinedTextField(
        value = value,
        onValueChange = { typed ->
            val cleaned = if (!fraction) {
                typed.filter(Char::isDigit)
            } else {
                // One separator, kept where it was typed, so ".5" and "0,5" both work out.
                val dotted = typed.replace(',', '.').filter { it.isDigit() || it == '.' }
                val first = dotted.indexOf('.')
                if (first < 0) dotted
                else dotted.substring(0, first + 1) + dotted.substring(first + 1).filter(Char::isDigit)
            }
            onValueChange(cleaned.take(maxDigits))
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (fraction) KeyboardType.Decimal else KeyboardType.Number
        ),
        // Two arrows stacked inside the field rather than buttons beside it: two of these fields
        // share a dialog row, and anything wider would leave no room for the number.
        trailingIcon = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StepArrow(Icons.Default.KeyboardArrowUp, R.string.cd_more) { step(1) }
                StepArrow(Icons.Default.KeyboardArrowDown, R.string.cd_less) { step(-1) }
            }
        },
        modifier = modifier,
    )
}

/** Millimeters of [px] dots, without the trailing zeros a plain conversion leaves behind. */
private fun mmText(px: Int, perMm: Float): String = trimmed(px / perMm)

private fun trimmed(mm: Float): String =
    if (mm == mm.toInt().toFloat()) mm.toInt().toString() else mm.toString().trimEnd('0')

/**
 * Dots of a typed millimeter value, snapped to the dot grid and held inside the allowed range.
 * Anything unreadable falls back to the default margin rather than to zero, which would silently
 * print flush to the edge.
 */
private fun mmPx(text: String, perMm: Float): Int {
    val mm = text.replace(',', '.').toFloatOrNull() ?: return (perMm).roundToInt()
    return (mm * perMm).roundToInt().coerceIn(
        (LabelSpec.MIN_MARGIN_MM * perMm).roundToInt(),
        (LabelSpec.MAX_MARGIN_MM * perMm).roundToInt(),
    )
}

/** One half of the spinner in [MmField]. Half the height of the field, so both halves fit in it. */
@Composable
private fun StepArrow(icon: ImageVector, description: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 32.dp, height = 26.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = stringResource(description),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
