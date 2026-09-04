package io.github.toolicious.labler.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.toolicious.labler.BuildConfig
import io.github.toolicious.labler.R
import io.github.toolicious.labler.ble.BlePermissions
import io.github.toolicious.labler.ble.PrinterState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import io.github.toolicious.labler.printer.HeadGeometry
import io.github.toolicious.labler.printer.PrinterFamily
import io.github.toolicious.labler.printer.PrinterProtocols
import io.github.toolicious.labler.printer.TestPattern
import io.github.toolicious.labler.printer.Tunable
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Asks for the distance the user measured on the printed pattern. The dots the pattern puts
 * between its first and last tick are known, so the feed resolution follows from the one number a
 * ruler can actually give.
 */
@Composable
private fun LengthCalibrationDialog(
    spanDots: Int,
    declaredPitch: Float,
    onDismiss: () -> Unit,
    onMeasured: (Float) -> Unit,
) {
    // What the marks stand for, not what the current grid makes of them, so the number the user
    // is sent off to check is the same one every time.
    val expectedMm = TestPattern.CALIBRATION_MM.toFloat()
    var text by remember { mutableStateOf("") }
    val measured = text.replace(COMMA, DOT).toFloatOrNull()
    // Checked against the resolution the printer claims rather than against whatever is
    // currently stored, so a bad correction can still be measured away.
    val plausible = measured != null && measured > 0f &&
        spanDots / measured > declaredPitch / TestPattern.PLAUSIBLE_FACTOR &&
        spanDots / measured < declaredPitch * TestPattern.PLAUSIBLE_FACTOR
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calib_length_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.calib_length_body,
                        trimmedNumber(expectedMm),
                        // An example near enough the expected value to read as a plausible
                        // reading rather than as a second instruction.
                        exampleMm(expectedMm * 1.02f),
                    )
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    // Unit appended here rather than baked into the string, so the label can
                    // be reused where the unit is a different one. It also keeps "in" out of
                    // the English, where it sat right before mm and read as inches.
                    label = { Text("${stringResource(R.string.calib_length_field)} (mm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                // Only speaks up when there is something wrong with the number typed.
                if (measured != null && !plausible) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.calib_length_implausible, trimmedNumber(expectedMm)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { measured?.let(onMeasured) },
                enabled = plausible,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private const val COMMA = ','
private const val DOT = '.'

/**
 * The example reading in the calibration dialog, always with two decimals showing.
 *
 * Trimming it would be the wrong kindness here: the whole point of the example is that this is a
 * measurement worth taking to a hundredth of a millimeter, and a number ending in a round tenth
 * says the opposite. A zero in the last place is therefore nudged rather than dropped.
 */
private fun exampleMm(value: Float): String {
    val twoPlaces = String.format(Locale.US, "%.2f", value)
    return if (twoPlaces.endsWith("0")) twoPlaces.dropLast(1) + "1" else twoPlaces
}

/** A measurement without the trailing zeros a plain conversion leaves behind. */
private fun trimmedNumber(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString()
    else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

/** The label a calibration value carries in the settings list. */
private fun calibrationLabel(tunable: Tunable): Int = when (tunable) {
    Tunable.DOTS_PER_MM -> R.string.calib_dots_per_mm
    Tunable.HEAD_DOTS -> R.string.calib_head_dots
    Tunable.ROW_BIT_OFFSET -> R.string.calib_row_bit_offset
    Tunable.REVERSE_COLUMN_BYTES -> R.string.calib_reverse_column_bytes
    Tunable.AWAIT_PRINT_RESULT -> R.string.calib_await_print_result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenTestPrint: () -> Unit = {},
    onOpenFonts: () -> Unit = {},
    vm: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by vm.printerState.collectAsState()
    val info by vm.printerInfo.collectAsState()
    val saved by vm.savedPrinter.collectAsState()
    var showScanSheet by remember { mutableStateOf(false) }
    var showForgetConfirm by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val action = pendingAction
        pendingAction = null
        if (grants.values.all { it }) action?.invoke()
    }

    fun withPermissions(action: () -> Unit) {
        if (BlePermissions.allGranted(context)) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(BlePermissions.required())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(R.string.settings_printer), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val statusText = when (val s = state) {
                        is PrinterState.Disconnected -> stringResource(R.string.status_disconnected)
                        is PrinterState.Connecting -> stringResource(R.string.status_connecting, s.attempt)
                        is PrinterState.Ready ->
                            if (s.batteryPercent != null)
                                stringResource(R.string.status_ready_battery, s.name, s.batteryPercent)
                            else stringResource(R.string.status_ready, s.name)
                        is PrinterState.Printing -> stringResource(R.string.status_printing, (s.progress * 100).toInt())
                        is PrinterState.Error -> stringResource(R.string.status_error, s.message)
                    }
                    Text(statusText, style = MaterialTheme.typography.bodyMedium)

                    saved?.let {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_saved, it.name, it.address),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            // Accent-colored X: forgets the saved printer (after confirmation).
                            IconButton(
                                onClick = { showForgetConfirm = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_forget),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (state is PrinterState.Ready && info != null) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        info?.model?.let { Text(stringResource(R.string.info_model, it), style = MaterialTheme.typography.bodySmall) }
                        info?.firmware?.let { Text(stringResource(R.string.info_firmware, it), style = MaterialTheme.typography.bodySmall) }
                        info?.hardware?.let { Text(stringResource(R.string.info_hardware, it), style = MaterialTheme.typography.bodySmall) }
                        info?.serial?.let { Text(stringResource(R.string.info_serial, it), style = MaterialTheme.typography.bodySmall) }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val savedDisconnected = saved != null && state is PrinterState.Disconnected
                        when {
                            savedDisconnected -> {
                                // Saved printer, disconnected: connecting is the primary action.
                                Button(onClick = { withPermissions { vm.reconnectSaved() } }) {
                                    Text(stringResource(R.string.action_connect))
                                }
                                OutlinedButton(onClick = { withPermissions { showScanSheet = true } }) {
                                    Text(stringResource(R.string.scan_title))
                                }
                            }
                            state is PrinterState.Ready -> {
                                // Connected: nothing needs to be emphasized.
                                OutlinedButton(onClick = { withPermissions { showScanSheet = true } }) {
                                    Text(stringResource(R.string.scan_title))
                                }
                                OutlinedButton(onClick = { vm.disconnect() }) {
                                    Text(stringResource(R.string.action_disconnect))
                                }
                            }
                            else -> {
                                // No saved printer (or connecting): scanning is the primary action.
                                Button(
                                    onClick = { withPermissions { showScanSheet = true } },
                                    enabled = state !is PrinterState.Printing
                                ) { Text(stringResource(R.string.scan_title)) }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.settings_fonts), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onOpenFonts) { Text(stringResource(R.string.fonts_manage)) }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_diagnostics), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onOpenTestPrint) { Text(stringResource(R.string.settings_testtools)) }

            // The feed resolution is a value a user corrects for their own device; the rest are
            // guesses about a printer nobody here owns and only show in a development build.
            val calibrationFamily by vm.calibrationFamily.collectAsState()
            calibrationFamily?.let { family ->
                val calibration by vm.calibration.collectAsState()
                val declared = remember(family) { PrinterProtocols.baseOf(family) }
                val offered = declared.tunables.filter {
                    BuildConfig.DEBUG || it.availability == Tunable.Availability.RELEASE
                }
                if (offered.isEmpty()) return@let
                var measuring by remember { mutableStateOf(false) }
                var confirmingReset by remember { mutableStateOf(false) }

                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.settings_calibration),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_calibration_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                offered.forEach { tunable ->
                    val label = stringResource(calibrationLabel(tunable))
                    val declaredValue = declared.tunableValue(tunable).orEmpty()
                    val stored = calibration.values[tunable]
                    when {
                        // Asking for dots per millimeter would be asking the wrong question. What
                        // the user has is a ruler and a printed pattern, so that is what is asked.
                        tunable == Tunable.DOTS_PER_MM -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val declaredPitch = declared.geometry.dotsPerMm
                            val pitch = stored?.toFloatOrNull() ?: declaredPitch
                            val measured by vm.calibrationMeasurement.collectAsState()
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                // What the user typed, kept in front of them so the step from it
                                // to the dots per millimeter is there to follow, with the way back
                                // right beside it.
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        measured?.toFloatOrNull()?.let {
                                            stringResource(R.string.calib_length_measured, trimmedNumber(it))
                                        } ?: stringResource(R.string.calib_length_none),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (stored != null) {
                                        Text(
                                            stringResource(R.string.calib_reset_length),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .padding(start = 8.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { confirmingReset = true }
                                                .padding(horizontal = 6.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                                // Only worth a line once the two differ. Both units, because
                                // the app computes in dots per millimeter while every data
                                // sheet quotes dpi.
                                if (stored != null) {
                                    Text(
                                        stringResource(
                                            R.string.calib_length_current,
                                            trimmedNumber(pitch),
                                            trimmedNumber(declaredPitch),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        stringResource(
                                            R.string.calib_length_dpi,
                                            trimmedNumber(pitch * HeadGeometry.MM_PER_INCH),
                                            trimmedNumber(declaredPitch * HeadGeometry.MM_PER_INCH),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            OutlinedButton(onClick = { measuring = true }) {
                                Text(stringResource(R.string.calib_length_action))
                            }
                        }
                        tunable.kind == Tunable.Kind.FLAG -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = (stored ?: declaredValue).toBooleanStrictOrNull() ?: false,
                                onCheckedChange = { vm.setCalibration(tunable, it.toString()) },
                            )
                        }
                        else -> OutlinedTextField(
                            value = stored.orEmpty(),
                            onValueChange = { vm.setCalibration(tunable, it) },
                            label = { Text(label) },
                            placeholder = { Text(stringResource(R.string.calib_default, declaredValue)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                // Only worth its own button where a family offers more than the one value the
                // row above already resets.
                if (offered.size > 1 && calibration.values.isNotEmpty()) {
                    TextButton(onClick = { vm.resetCalibration() }) {
                        Text(stringResource(R.string.calib_reset))
                    }
                }

                if (confirmingReset) {
                    AlertDialog(
                        onDismissRequest = { confirmingReset = false },
                        title = { Text(stringResource(R.string.calib_reset_confirm_title)) },
                        text = { Text(stringResource(R.string.calib_reset_confirm_text)) },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmingReset = false
                                vm.resetLengthCalibration()
                            }) { Text(stringResource(R.string.calib_reset_length)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmingReset = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        },
                    )
                }

                if (measuring) {
                    LengthCalibrationDialog(
                        spanDots = TestPattern.calibrationSpanDots(declared.geometry),
                        declaredPitch = declared.geometry.dotsPerMm,
                        onDismiss = { measuring = false },
                        onMeasured = { vm.calibrateFromMeasurement(it); measuring = false },
                    )
                }
            }

            // A Phomemo command, and one the 0x1F family only documents rather than confirms, so
            // it stays a probe for development instead of something a release offers. The protocol
            // side is untouched, and a level saved in a debug build keeps working.
            val printerFamily = (state as? PrinterState.Ready)?.family
                ?: saved?.family
                ?: PrinterFamily.DEFAULT
            if (BuildConfig.DEBUG && printerFamily == PrinterFamily.PHOMEMO) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.settings_experimental), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.settings_experimental_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                val savedDensity by vm.printDensity.collectAsState()
                // Local slider position; persisted only when the drag ends, so DataStore is not
                // hammered on every tick. remember(savedDensity) re-syncs if it changes elsewhere.
                var densitySlider by remember(savedDensity) { mutableFloatStateOf(savedDensity.toFloat()) }
                val densityLevel = densitySlider.roundToInt()
                val densityText =
                    if (densityLevel == 0) stringResource(R.string.exp_density_off) else densityLevel.toString()
                Text(
                    stringResource(R.string.exp_density_value, densityText),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = densitySlider,
                    onValueChange = { densitySlider = it },
                    onValueChangeFinished = { vm.setPrintDensity(densitySlider.roundToInt()) },
                    valueRange = 0f..15f,
                    steps = 14,
                )
                Text(
                    stringResource(R.string.exp_density_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showForgetConfirm) {
        AlertDialog(
            onDismissRequest = { showForgetConfirm = false },
            title = { Text(stringResource(R.string.forget_title)) },
            text = { Text(stringResource(R.string.forget_message, saved?.name ?: "")) },
            confirmButton = {
                TextButton(onClick = { showForgetConfirm = false; vm.forget() }) {
                    Text(stringResource(R.string.action_forget))
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgetConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showScanSheet) {
        ScanSheet(
            vm = vm,
            onDismiss = {
                vm.stopScan()
                showScanSheet = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanSheet(vm: SettingsViewModel, onDismiss: () -> Unit) {
    val scanning by vm.scanning.collectAsState()
    val results by vm.visibleResults.collectAsState()
    val showAll by vm.showAll.collectAsState()
    val scanError by vm.scanError.collectAsState()

    LaunchedEffect(Unit) { vm.startScan() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = showAll, onCheckedChange = { vm.setShowAll(it) })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.scan_show_all), style = MaterialTheme.typography.bodyMedium)
            }
            if (scanning) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            scanError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            if (!scanning && results.isEmpty() && scanError == null) {
                Text(stringResource(R.string.scan_empty), style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn {
                items(results, key = { it.device.address }) { found ->
                    ListItem(
                        headlineContent = { Text(found.name) },
                        supportingContent = { Text(stringResource(R.string.scan_device_line, found.device.address, found.rssi)) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingContent = {
                            TextButton(onClick = {
                                vm.connectTo(found)
                                onDismiss()
                            }) { Text(stringResource(R.string.action_connect)) }
                        }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
