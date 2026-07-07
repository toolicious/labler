package io.github.toolicious.labler.ui.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.toolicious.labler.R
import io.github.toolicious.labler.data.PrintHistoryEntry
import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.printer.MonoImage
import io.github.toolicious.labler.render.LabelRenderer
import io.github.toolicious.labler.ui.components.rememberBlePermissionRunner
import io.github.toolicious.labler.ui.print.PrintSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, vm: HistoryViewModel = viewModel()) {
    val entries by vm.entries.collectAsState()
    var reprint by remember { mutableStateOf<Pair<MonoImage, PrintHistoryEntry>?>(null) }
    val withBlePermissions = rememberBlePermissionRunner()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        TextButton(onClick = vm::clear) { Text(stringResource(R.string.action_clear)) }
                    }
                }
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                HistoryCard(
                    entry = entry,
                    onReprint = {
                        withBlePermissions {
                            reprint = LabelRenderer.renderMono(entry.spec, entry.elements) to entry
                        }
                    },
                    onDelete = { vm.delete(entry.id) }
                )
            }
        }
    }

    reprint?.let { (image, entry) ->
        PrintSheet(
            image = image,
            initialMedia = entry.spec.media,
            onDismiss = { reprint = null }
        )
    }
}

@Composable
private fun HistoryCard(
    entry: PrintHistoryEntry,
    onReprint: () -> Unit,
    onDelete: () -> Unit,
) {
    Card {
        Column(Modifier.padding(12.dp)) {
            val bitmap = remember(entry.id) {
                LabelRenderer.render(entry.spec, entry.elements).asImageBitmap()
            }
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(entry.spec.lengthPx.toFloat() / LabelSpec.PRINT_HEIGHT_PX)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
                contentScale = ContentScale.FillBounds
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.templateName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    val dateText = remember(entry.id) {
                        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(entry.printedAt))
                    }
                    Text(
                        stringResource(
                            R.string.history_meta,
                            dateText,
                            entry.copies,
                            entry.spec.tapeWidthMm,
                            entry.spec.lengthMm
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onReprint) { Text(stringResource(R.string.action_print)) }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete))
                }
            }
        }
    }
}
