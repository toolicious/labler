package io.github.toolicious.labler.ui.home

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.toolicious.labler.R
import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.model.LabelTemplate
import io.github.toolicious.labler.model.LengthMode
import io.github.toolicious.labler.printer.MediaType
import io.github.toolicious.labler.printer.Protocol
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
                        Text(stringResource(R.string.app_name))
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
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            painterResource(R.drawable.ic_history),
                            contentDescription = stringResource(R.string.cd_history)
                        )
                    }
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
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
        ) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.home_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = { if (query.isNotEmpty()) ClearButton { vm.setQuery("") } },
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            if (templates.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank()) stringResource(R.string.home_empty)
                        else stringResource(R.string.home_no_results),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(templates, key = { it.id }) { template ->
                        val copyName = stringResource(R.string.duplicate_name, template.name)
                        TemplateCard(
                            template = template,
                            // Favorite re-sorting glides with animation (the grid reflows).
                            modifier = Modifier.animateItem(),
                            onClick = { onOpenTemplate(template.id) },
                            onPrint = { withBlePermissions { printTarget = template } },
                            onToggleFavorite = { vm.toggleFavorite(template) },
                            onEdit = { editTarget = template },
                            onDuplicate = { vm.duplicate(template.id, copyName) },
                            onDelete = { deleteTarget = template },
                            onExport = {
                                exportTarget = template
                                exportLauncher.launch("${template.name}.labler.json")
                            },
                        )
                    }
                }
            }
        }
    }

    if (showInfoDialog) {
        InfoDialog(onDismiss = { showInfoDialog = false })
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
            initialSpec = LabelSpec(),
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
            // The revision is a key as well, so a thumbnail is re-rendered once a custom font
            // it references becomes available or is removed.
            val bitmap = remember(template.id, template.updatedAt, FontRegistry.revision) {
                LabelRenderer.render(template.spec, template.elements).asImageBitmap()
            }
            // An auto-length label is as long as its content, so the thumbnail cannot use the
            // stored length for its aspect ratio.
            val lengthPx = remember(template.id, template.updatedAt, FontRegistry.revision) {
                LabelRenderer.effectiveLengthPx(template.spec, template.elements)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(lengthPx.toFloat() / LabelSpec.PRINT_HEIGHT_PX)
                    .clip(labelShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, labelShape),
                contentScale = ContentScale.FillBounds
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    template.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.width(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_menu))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        // Accent-colored and first, because printing is the main action here;
                        // the same entry the long press on the card triggers.
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_print)) },
                            colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.primary),
                            onClick = { menuOpen = false; onPrint() })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_edit)) },
                            onClick = { menuOpen = false; onEdit() })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_duplicate)) },
                            onClick = { menuOpen = false; onDuplicate() })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_export)) },
                            onClick = { menuOpen = false; onExport() })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_delete)) },
                            onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
            // Dimensions on the left, favorite star in the bottom-right corner (there is free space there).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (template.spec.lengthIsAuto) {
                        // Reuses the length already measured for the aspect ratio above.
                        stringResource(
                            R.string.template_size_auto,
                            template.spec.tapeWidthMm,
                            lengthPx / Protocol.DOTS_PER_MM,
                        )
                    } else {
                        stringResource(R.string.template_size, template.spec.tapeWidthMm, template.spec.lengthMm)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleFavorite, modifier = Modifier.width(32.dp)) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = stringResource(R.string.cd_favorite),
                        tint = if (template.favorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
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
    val isPresetSize = LabelSpec.PRESETS.any { it.first == initialSpec.tapeWidthMm && it.second == initialSpec.lengthMm }
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autofocusName) nameFocus.requestFocus() }
    // Two separate "custom" flags: die-cut picks a whole stock size, continuous only a width.
    var customSize by rememberSaveable(initialSpec) { mutableStateOf(!isPresetSize) }
    var customWidth by rememberSaveable(initialSpec) {
        mutableStateOf(initialSpec.tapeWidthMm !in LabelSpec.TAPE_WIDTHS)
    }
    var widthText by rememberSaveable(initialSpec) { mutableStateOf(initialSpec.tapeWidthMm.toString()) }
    var lengthText by rememberSaveable(initialSpec) { mutableStateOf(initialSpec.lengthMm.toString()) }
    var dieCut by rememberSaveable(initialSpec) { mutableStateOf(initialSpec.media == MediaType.DIE_CUT) }
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

                if (dieCut) {
                    // Stock sizes: these presets are commercially available die-cut labels, so
                    // they only make sense here.
                    Text(stringResource(R.string.size_hint), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LabelSpec.PRESETS.forEach { (w, l) ->
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
                                modifier = Modifier.weight(1f),
                            )
                            MmField(
                                value = lengthText,
                                onValueChange = { lengthText = it },
                                label = stringResource(R.string.field_length_mm),
                                maxDigits = 3,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else {
                    // Continuous tape: the width is the cartridge, the length is a design choice.
                    Text(stringResource(R.string.field_tape_width), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LabelSpec.TAPE_WIDTHS.forEach { w ->
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
                    // One field in every mode; only its meaning changes, from exact to lower
                    // bound. In the manual mode it is the same number the edges carry, so typing
                    // one moves the trailing edge, which is the one the length belongs to.
                    MmField(
                        value = lengthText,
                        onValueChange = { lengthText = it },
                        label = stringResource(
                            if (lengthMode == LengthMode.VARIABLE) R.string.field_min_length_mm
                            else R.string.field_length_mm
                        ),
                        maxDigits = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
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
                    ?.coerceIn(LabelSpec.MIN_TAPE_MM, LabelSpec.MAX_TAPE_MM) ?: 12
                val length = lengthText.toIntOrNull()
                    ?.coerceIn(LabelSpec.MIN_LENGTH_MM, LabelSpec.MAX_LENGTH_MM) ?: 40
                val media = if (dieCut) MediaType.DIE_CUT else MediaType.CONTINUOUS
                val mode = if (dieCut) LengthMode.FIXED else lengthMode
                // A die-cut label is always fixed, its length belongs to the stock.
                onConfirm(name, LabelSpec(width, length, media).withLengthMode(mode))
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

/** Numeric millimetre field of the label dialog (digits only, capped length). */
@Composable
private fun MmField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    maxDigits: Int,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(maxDigits)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
