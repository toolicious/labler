package io.github.toolicious.labler.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.toolicious.labler.R
import io.github.toolicious.labler.ble.PrinterState

/**
 * Printer status shown as a small "pill". Deliberately not an AssistChip so that the
 * left/right inner padding stays controllable. Background per state, white contents, and in
 * the Ready state with a battery value a battery icon directly before the percentage.
 */
@Composable
fun PrinterStatusChip(state: PrinterState, permissionMissing: Boolean = false, onClick: () -> Unit) {
    val text = if (permissionMissing) stringResource(R.string.chip_permission) else when (state) {
        is PrinterState.Disconnected -> stringResource(R.string.chip_no_printer)
        is PrinterState.Connecting -> stringResource(R.string.chip_connecting, state.attempt)
        is PrinterState.Ready -> state.name.removeSuffix("_BLE")
        is PrinterState.Printing -> stringResource(R.string.chip_printing, (state.progress * 100).toInt())
        is PrinterState.Error -> stringResource(R.string.chip_error)
    }
    // Background per state: connected greenish, disconnected/error reddish, connecting/permission amber.
    val container = if (permissionMissing) Color(0xFFA66E38) else when (state) {
        is PrinterState.Ready, is PrinterState.Printing -> Color(0xFF457A52)
        is PrinterState.Connecting -> Color(0xFFA66E38)
        is PrinterState.Disconnected, is PrinterState.Error -> Color(0xFF9E534E)
    }
    val battery = if (permissionMissing) null else (state as? PrinterState.Ready)?.batteryPercent
    // Custom pill, but with a guaranteed 48-dp touch target and button role (accessibility);
    // the visible pill stays compact and is centered within the larger click area.
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(container)
                .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        if (permissionMissing) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
        } else when (state) {
            is PrinterState.Connecting, is PrinterState.Printing ->
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
            is PrinterState.Ready ->
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
            is PrinterState.Error ->
                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
            is PrinterState.Disconnected ->
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
        }
        Spacer(Modifier.width(6.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
        if (battery != null) {
            Icon(
                painterResource(R.drawable.ic_battery),
                contentDescription = null,
                modifier = Modifier.padding(start = 6.dp, end = 1.dp).size(15.dp),
                tint = Color.White,
            )
            Text("$battery %", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
