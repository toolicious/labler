package io.github.toolicious.labler.ui.testprint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.toolicious.labler.R
import io.github.toolicious.labler.printer.MediaType
import io.github.toolicious.labler.printer.MonoImage
import io.github.toolicious.labler.printer.TestPattern
import io.github.toolicious.labler.render.TextTestRenderer
import io.github.toolicious.labler.ui.components.ClearButton
import io.github.toolicious.labler.ui.components.PrinterStatusChip
import io.github.toolicious.labler.ui.components.rememberBlePermissionRunner
import io.github.toolicious.labler.ui.print.PrintSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestPrintScreen(onOpenSettings: () -> Unit, vm: TestPrintViewModel = viewModel()) {
    val printerState by vm.printerState.collectAsState()
    var text by remember { mutableStateOf("Ää Üü ß 0123 iIlL1") }
    var media by remember { mutableStateOf(MediaType.DIE_CUT) }
    var sheetImage by remember { mutableStateOf<MonoImage?>(null) }
    val withBlePermissions = rememberBlePermissionRunner()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.testprint_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = { PrinterStatusChip(printerState, onClick = onOpenSettings) }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.print_paper), style = MaterialTheme.typography.bodyMedium)
                FilterChip(
                    selected = media == MediaType.DIE_CUT,
                    onClick = { media = MediaType.DIE_CUT },
                    label = { Text(stringResource(R.string.media_die_cut)) }
                )
                FilterChip(
                    selected = media == MediaType.CONTINUOUS,
                    onClick = { media = MediaType.CONTINUOUS },
                    label = { Text(stringResource(R.string.media_continuous)) }
                )
            }
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.testprint_geometry), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { withBlePermissions { sheetImage = TestPattern.create(320) } }) {
                Text(stringResource(R.string.testprint_show))
            }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.testprint_text_label), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { if (text.isNotEmpty()) ClearButton { text = "" } },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { withBlePermissions { sheetImage = TextTestRenderer.render(text) } }) {
                Text(stringResource(R.string.testprint_show))
            }
        }
    }

    sheetImage?.let { image ->
        PrintSheet(
            image = image,
            initialMedia = media,
            onDismiss = { sheetImage = null }
        )
    }
}
