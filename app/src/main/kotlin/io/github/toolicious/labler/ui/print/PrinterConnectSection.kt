package io.github.toolicious.labler.ui.print

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.toolicious.labler.R
import io.github.toolicious.labler.ble.PrinterState
import io.github.toolicious.labler.ui.components.rememberBlePermissionState

/**
 * Printer status in the print dialog. Shows whether a printer is connected, and when
 * there is no connection, offers ways to connect (remembered device) or to open the
 * printer settings. The actual printing is only enabled by the calling dialog when
 * [PrinterState.Ready].
 */
@Composable
fun PrinterConnectSection(
    state: PrinterState,
    hasSavedPrinter: Boolean,
    onConnect: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connecting = state is PrinterState.Connecting
    val perm = rememberBlePermissionState()
    val statusText = when (state) {
        is PrinterState.Disconnected -> stringResource(R.string.status_disconnected)
        is PrinterState.Connecting -> stringResource(R.string.status_connecting, state.attempt)
        is PrinterState.Ready ->
            if (state.batteryPercent != null)
                stringResource(R.string.status_ready_battery, state.name, state.batteryPercent)
            else stringResource(R.string.status_ready, state.name)
        is PrinterState.Printing -> stringResource(R.string.status_printing, (state.progress * 100).toInt())
        is PrinterState.Error -> stringResource(R.string.status_error, state.message)
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (state) {
                is PrinterState.Ready ->
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                is PrinterState.Connecting, is PrinterState.Printing ->
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                is PrinterState.Error ->
                    Icon(Icons.Default.Warning, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                is PrinterState.Disconnected ->
                    Icon(Icons.Default.Close, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(statusText, style = MaterialTheme.typography.bodyMedium)
        }

        if (state !is PrinterState.Ready && state !is PrinterState.Printing) {
            Spacer(Modifier.height(6.dp))
            if (!perm.granted) {
                Text(
                    stringResource(R.string.perm_bluetooth_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = perm.request) {
                        Text(stringResource(R.string.action_grant_permission))
                    }
                    OutlinedButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.print_printer_settings))
                    }
                }
            } else {
                Text(
                    stringResource(R.string.print_need_printer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasSavedPrinter) {
                        Button(onClick = onConnect, enabled = !connecting) {
                            Text(stringResource(R.string.action_connect))
                        }
                    }
                    OutlinedButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.print_printer_settings))
                    }
                }
            }
        }
    }
}
