package io.github.toolicious.labler.ui.print

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.toolicious.labler.R
import io.github.toolicious.labler.ble.PrinterState
import io.github.toolicious.labler.printer.MediaType
import io.github.toolicious.labler.printer.MonoImage
import io.github.toolicious.labler.render.MonoConverter

/**
 * Print dialog with pixel-exact 1-bit preview (exactly what the printer
 * receives), copy count and paper-type toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintSheet(
    image: MonoImage,
    initialMedia: MediaType,
    onDismiss: () -> Unit,
    onPrinted: (copies: Int, media: MediaType) -> Unit = { _, _ -> },
    vm: PrintViewModel = viewModel(),
) {
    val working by vm.working.collectAsState()
    val error by vm.error.collectAsState()
    val done by vm.done.collectAsState()
    val printerState by vm.printerState.collectAsState()
    val savedPrinter by vm.savedPrinter.collectAsState(initial = null)

    var copies by remember { mutableIntStateOf(1) }
    var media by remember { mutableStateOf(initialMedia) }
    val preview = remember(image) { MonoConverter.toBitmap(image).asImageBitmap() }

    val view = LocalView.current
    DisposableEffect(working) {
        view.keepScreenOn = working
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(done) {
        if (done) {
            onPrinted(copies, media)
            vm.reset()
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!working) onDismiss() }) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.action_print), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Text(
                stringResource(R.string.print_preview, image.width / 8),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Image(
                bitmap = preview,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(image.width.toFloat() / image.height)
                    .border(1.dp, MaterialTheme.colorScheme.outline),
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.None
            )
            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.print_paper), style = MaterialTheme.typography.bodyMedium)
                FilterChip(
                    selected = media == MediaType.DIE_CUT,
                    onClick = { media = MediaType.DIE_CUT },
                    label = { Text(stringResource(R.string.media_die_cut)) },
                    enabled = !working
                )
                FilterChip(
                    selected = media == MediaType.CONTINUOUS,
                    onClick = { media = MediaType.CONTINUOUS },
                    label = { Text(stringResource(R.string.media_continuous)) },
                    enabled = !working
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.print_copies), style = MaterialTheme.typography.bodyMedium)
                IconButton(
                    onClick = { if (copies > 1) copies-- },
                    enabled = !working && copies > 1
                ) { Text("-", style = MaterialTheme.typography.titleLarge) }
                Text("$copies", style = MaterialTheme.typography.titleMedium)
                IconButton(
                    onClick = { if (copies < 100) copies++ },
                    enabled = !working && copies < 100
                ) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
            Spacer(Modifier.height(8.dp))

            if (printerState !is PrinterState.Printing) {
                PrinterConnectSection(
                    state = printerState,
                    hasSavedPrinter = savedPrinter != null,
                    onConnect = { vm.connect() },
                    onOpenSettings = onDismiss,
                )
                Spacer(Modifier.height(8.dp))
            }

            val printing = printerState as? PrinterState.Printing
            if (printing != null) {
                Text(
                    stringResource(R.string.print_label_progress, printing.copy, printing.copies),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { printing.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (working) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { vm.print(image, media, copies) },
                    enabled = !working && printerState is PrinterState.Ready
                ) { Text(stringResource(R.string.action_print)) }
                OutlinedButton(onClick = onDismiss, enabled = !working) { Text(stringResource(R.string.action_cancel)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
