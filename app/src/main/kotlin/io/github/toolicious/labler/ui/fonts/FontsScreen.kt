package io.github.toolicious.labler.ui.fonts

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.toolicious.labler.R
import io.github.toolicious.labler.data.CustomFont
import io.github.toolicious.labler.data.CustomFontRepository.AddResult
import io.github.toolicious.labler.ui.components.labelFontFamily

/**
 * Manages the fonts the user added. Adding, removing and swapping a file all happen here rather
 * than per template, because a font belongs to the device, not to a single label.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontsScreen(
    onBack: () -> Unit,
    vm: FontsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val fonts by vm.fonts.collectAsState()

    var renaming by remember { mutableStateOf<CustomFont?>(null) }
    var removing by remember { mutableStateOf<CustomFont?>(null) }
    // Saveable, because it has to survive the activity being recreated behind the document
    // picker; otherwise the picked file comes back with nothing to attach it to.
    var replacing by rememberSaveable { mutableStateOf<String?>(null) }

    // Font MIME types are unreliable across document providers ("font/ttf", "application/x-font-ttf",
    // often just "application/octet-stream"), so a narrow filter would grey out valid files. Parsing
    // the picked file is the actual validation.
    val anyFile = arrayOf("*/*")

    val addLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) vm.add(uris) { results -> context.toastResults(results) }
    }
    val replaceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val family = replacing
        replacing = null
        if (uri != null && family != null) {
            vm.replaceFile(family, uri) { result -> context.toastResults(listOf(result)) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fonts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { addLauncher.launch(anyFile) }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.fonts_add))
                    }
                },
            )
        }
    ) { padding ->
        if (fonts.isEmpty()) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.fonts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(fonts, key = { it.family }) { font ->
                    FontRow(
                        font = font,
                        onRename = { renaming = font },
                        onReplace = {
                            replacing = font.family
                            replaceLauncher.launch(anyFile)
                        },
                        onRemove = { removing = font },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    renaming?.let { font ->
        RenameDialog(
            font = font,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                vm.rename(font.family, name)
                renaming = null
            },
        )
    }

    removing?.let { font ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.fonts_delete_title, font.label)) },
            text = { Text(stringResource(R.string.fonts_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.remove(font.family)
                    removing = null
                }) { Text(stringResource(R.string.fonts_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun FontRow(
    font: CustomFont,
    onRename: () -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = {
            Text(
                font.label,
                fontFamily = labelFontFamily(customFamily = font.family),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                // The family name is the key templates store, so it stays visible once a custom
                // display name hides it in the headline.
                if (font.displayName.isNotEmpty()) {
                    Text(font.family, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    stringResource(R.string.fonts_source, font.sourceName),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_menu))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fonts_rename)) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fonts_replace)) },
                        onClick = {
                            menuOpen = false
                            onReplace()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fonts_delete)) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun RenameDialog(font: CustomFont, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember(font.family) { mutableStateOf(font.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fonts_rename_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    // The family name comes out of the font file and can be long.
                    label = { Text(font.family, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                )
                Text(
                    stringResource(R.string.fonts_rename_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Reports an import, matching how the template import surfaces its outcome. */
private fun Context.toastResults(results: List<AddResult>) {
    val added = results.count { it is AddResult.Added }
    val message = when {
        results.size == 1 -> when (val only = results.first()) {
            is AddResult.Added -> getString(R.string.toast_font_added, only.font.label)
            is AddResult.Duplicate -> getString(R.string.err_font_duplicate, only.family)
            AddResult.Invalid -> getString(R.string.err_font_invalid)
            AddResult.TooLarge -> getString(R.string.err_font_too_large)
            AddResult.Failed -> getString(R.string.err_file_not_readable)
        }

        added == results.size -> getString(R.string.toast_fonts_added, added)
        else -> getString(R.string.toast_fonts_added_partial, added, results.size)
    }
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
