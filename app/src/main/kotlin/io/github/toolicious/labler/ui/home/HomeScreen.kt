package io.github.toolicious.labler.ui.home

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.toolicious.labler.R
import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.model.LabelTemplate
import io.github.toolicious.labler.printer.MediaType
import io.github.toolicious.labler.render.LabelRenderer
import io.github.toolicious.labler.ui.components.ClearButton
import io.github.toolicious.labler.ui.components.PrinterStatusChip
import io.github.toolicious.labler.ui.components.rememberBlePermissionState
import io.github.toolicious.labler.ui.info.InfoDialog

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
    var showNewDialog by rememberSaveable { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<LabelTemplate?>(null) }
    var exportTarget by remember { mutableStateOf<LabelTemplate?>(null) }
    var deleteTarget by remember { mutableStateOf<LabelTemplate?>(null) }
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
            onImport = null
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
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    ElevatedCard(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(10.dp)) {
            val bitmap = remember(template.id, template.updatedAt) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(template.spec.lengthPx.toFloat() / LabelSpec.PRINT_HEIGHT_PX)
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
                    stringResource(R.string.template_size, template.spec.tapeWidthMm, template.spec.lengthMm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LabelDialog(
    title: String,
    initialName: String,
    initialSpec: LabelSpec,
    onDismiss: () -> Unit,
    onConfirm: (String, LabelSpec) -> Unit,
    onImport: (() -> Unit)?,
    autofocusName: Boolean = false,
) {
    val isPreset = LabelSpec.PRESETS.any { it.first == initialSpec.tapeWidthMm && it.second == initialSpec.lengthMm }
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autofocusName) nameFocus.requestFocus() }
    var custom by rememberSaveable(initialSpec) { mutableStateOf(!isPreset) }
    var widthText by rememberSaveable(initialSpec) { mutableStateOf(initialSpec.tapeWidthMm.toString()) }
    var lengthText by rememberSaveable(initialSpec) { mutableStateOf(initialSpec.lengthMm.toString()) }
    var dieCut by rememberSaveable(initialSpec) { mutableStateOf(initialSpec.media == MediaType.DIE_CUT) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                Text(stringResource(R.string.size_hint), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabelSpec.PRESETS.forEach { (w, l) ->
                        FilterChip(
                            selected = !custom && widthText == "$w" && lengthText == "$l",
                            onClick = {
                                custom = false
                                widthText = "$w"
                                lengthText = "$l"
                            },
                            label = { Text("${w}x$l", maxLines = 1, softWrap = false) }
                        )
                    }
                    FilterChip(
                        selected = custom,
                        onClick = { custom = true },
                        label = { Text(stringResource(R.string.preset_custom), maxLines = 1, softWrap = false) }
                    )
                }
                if (custom || !dieCut) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (custom) {
                            OutlinedTextField(
                                value = widthText,
                                onValueChange = { widthText = it.filter(Char::isDigit).take(2) },
                                label = { Text(stringResource(R.string.field_tape_mm)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = lengthText,
                            onValueChange = { lengthText = it.filter(Char::isDigit).take(3) },
                            label = { Text(stringResource(R.string.field_length_mm)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = dieCut,
                        onClick = { dieCut = true },
                        label = { Text(stringResource(R.string.media_die_cut), maxLines = 1, softWrap = false) }
                    )
                    FilterChip(
                        selected = !dieCut,
                        onClick = { dieCut = false },
                        label = { Text(stringResource(R.string.media_continuous), maxLines = 1, softWrap = false) }
                    )
                }
            }
        },
        confirmButton = {
            val submit = {
                val width = widthText.toIntOrNull()?.coerceIn(10, 15) ?: 12
                val length = lengthText.toIntOrNull()?.coerceIn(10, 500) ?: 40
                val media = if (dieCut) MediaType.DIE_CUT else MediaType.CONTINUOUS
                onConfirm(name, LabelSpec(width, length, media))
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
