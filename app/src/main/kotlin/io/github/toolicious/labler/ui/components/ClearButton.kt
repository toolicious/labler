package io.github.toolicious.labler.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.toolicious.labler.R

/** Subtle "clear field" button (small, muted X) for the trailingIcon of a text field. */
@Composable
fun ClearButton(onClear: () -> Unit) {
    IconButton(onClick = onClear) {
        Icon(
            Icons.Default.Clear,
            contentDescription = stringResource(R.string.cd_clear),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}
