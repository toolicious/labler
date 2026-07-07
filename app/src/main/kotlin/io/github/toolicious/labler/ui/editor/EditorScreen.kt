package io.github.toolicious.labler.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.toolicious.labler.R
import io.github.toolicious.labler.model.BarcodeElement
import io.github.toolicious.labler.model.FrameElement
import io.github.toolicious.labler.model.FrameStyle
import io.github.toolicious.labler.model.IconElement
import io.github.toolicious.labler.model.ImageElement
import io.github.toolicious.labler.model.LabelElement
import io.github.toolicious.labler.model.LabelFont
import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.model.LabelTextAlign
import io.github.toolicious.labler.model.QrPayload
import io.github.toolicious.labler.model.QrPayloadType
import io.github.toolicious.labler.model.Symbology
import io.github.toolicious.labler.model.TextElement
import io.github.toolicious.labler.printer.dither.DitherMode
import io.github.toolicious.labler.printer.dither.OutlineMethod
import io.github.toolicious.labler.render.LabelRenderer
import io.github.toolicious.labler.ui.components.ClearButton
import io.github.toolicious.labler.ui.components.rememberBlePermissionRunner
import io.github.toolicious.labler.ui.home.LabelDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import io.github.toolicious.labler.ui.print.TemplatePrintSheet
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    templateId: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    vm: EditorViewModel = viewModel(factory = EditorViewModel.factory(templateId)),
) {
    val template by vm.template.collectAsState()
    val selectedId by vm.selectedId.collectAsState()
    val selected by vm.selectedElement.collectAsState()
    val guides by vm.guides.collectAsState()
    val canUndo by vm.canUndo.collectAsState()
    val canRedo by vm.canRedo.collectAsState()
    var showPrintSheet by remember { mutableStateOf(false) }
    var showMetaDialog by remember { mutableStateOf(false) }
    val withBlePermissions = rememberBlePermissionRunner()

    val t = template
    val context = LocalContext.current
    val importScope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) importScope.launch {
            val loaded = withContext(Dispatchers.IO) { ImageImport.load(context, uri) }
            if (loaded != null) {
                vm.addElement(
                    ImageElement(
                        id = UUID.randomUUID().toString(),
                        pngBase64 = loaded.pngBase64,
                        srcWidth = loaded.width,
                        srcHeight = loaded.height,
                        // Default to fit within the label height (no clipping), so the box matches the
                        // image. The lower bound is capped so a very tall/narrow image cannot exceed 96 px.
                        widthPx = (76f * loaded.width / loaded.height)
                            .coerceIn(minOf(16f, 96f * loaded.width / loaded.height), 480f),
                    )
                )
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Tapping the name opens the edit dialog (name + size, applied immediately).
                    Text(
                        t?.name ?: stringResource(R.string.app_name),
                        maxLines = 1,
                        modifier = if (t != null) Modifier.clickable { showMetaDialog = true } else Modifier,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = vm::undo, enabled = canUndo) {
                        Text("↶", style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = vm::redo, enabled = canRedo) {
                        Text("↷", style = MaterialTheme.typography.titleLarge)
                    }
                    Button(
                        onClick = { withBlePermissions { showPrintSheet = true } },
                        enabled = t != null,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_print),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_print))
                    }
                }
            )
        }
    ) { padding ->
        if (t == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 12.dp)
                .fillMaxSize()
        ) {
            EditorCanvas(
                spec = t.spec,
                elements = t.elements,
                selectedId = selectedId,
                guides = guides,
                onSelect = vm::select,
                onDragStart = vm::beginDrag,
                onDragBy = vm::dragBy,
                onDragEnd = vm::endDrag,
                onResizeStart = vm::beginResize,
                onResizeBy = vm::resizeSelectedBy,
                onResizeEnd = vm::endResize,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
            // Only the label (canvas) stays fixed; everything below it scrolls. imePadding must come
            // BEFORE verticalScroll so it shrinks the scroll viewport to the keyboard edge (not just
            // the content); otherwise, with edge-to-edge, the viewport reaches behind the keyboard and
            // the focused field auto-scrolls behind it instead of above it.
            Column(
                Modifier
                    .weight(1f)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
            ) {
            Text(
                stringResource(R.string.template_size, t.spec.tapeWidthMm, t.spec.lengthMm),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp),
            )

            GroupLabel(stringResource(R.string.group_add))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AddButton(stringResource(R.string.add_text)) {
                    vm.addElement(TextElement(id = UUID.randomUUID().toString(), x = 8f, y = 32f))
                }
                AddButton(stringResource(R.string.add_symbol)) {
                    vm.addElement(IconElement(id = UUID.randomUUID().toString(), x = 8f, y = 24f))
                }
                AddButton(stringResource(R.string.add_image)) {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                AddButton(stringResource(R.string.add_frame)) {
                    val id = UUID.randomUUID().toString()
                    val sel = selected
                    val frame = if (sel != null) {
                        // Fit the frame snugly around the selected element.
                        val s = LabelRenderer.measure(sel)
                        val rotated = sel.rotation % 180 != 0
                        val w = if (rotated) s.height else s.width
                        val h = if (rotated) s.width else s.height
                        val cx = sel.x + s.width / 2f
                        val cy = sel.y + s.height / 2f
                        val pad = 6f
                        FrameElement(
                            id = id,
                            x = cx - w / 2f - pad,
                            y = cy - h / 2f - pad,
                            widthPx = w + 2 * pad,
                            heightPx = h + 2 * pad
                        )
                    } else {
                        FrameElement(
                            id = id,
                            x = 2f, y = 2f,
                            widthPx = (t.spec.lengthPx - 4).toFloat(),
                            heightPx = (LabelSpec.PRINT_HEIGHT_PX - 4).toFloat()
                        )
                    }
                    vm.addElement(frame)
                }
                AddButton(stringResource(R.string.add_barcode)) {
                    vm.addElement(BarcodeElement(id = UUID.randomUUID().toString(), x = 8f, y = 16f))
                }
            }

            if (t.elements.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                GroupLabel(stringResource(R.string.group_elements))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    t.elements.forEach { element ->
                        ChoiceChip(
                            selected = element.id == selectedId,
                            onClick = { vm.select(if (element.id == selectedId) null else element.id) },
                            label = { ElementChipLabel(element) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            selected?.let { element ->
                    PropertiesPanel(
                        element = element,
                        onUpdate = vm::updateElement,
                        onDelete = vm::deleteSelected
                    )
                } ?: Text(
                    stringResource(R.string.editor_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showPrintSheet && t != null) {
        TemplatePrintSheet(
            template = t,
            onDismiss = { showPrintSheet = false },
            onOpenSettings = onOpenSettings
        )
    }

    if (showMetaDialog && t != null) {
        LabelDialog(
            title = stringResource(R.string.dialog_edit_title),
            initialName = t.name,
            initialSpec = t.spec,
            onDismiss = { showMetaDialog = false },
            onConfirm = { name, spec ->
                vm.updateMeta(name, spec)
                showMetaDialog = false
            },
            onImport = null,
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

/** Filled "+ word" button for adding; clearly set apart from the selection chips. */
@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(start = 6.dp, end = 10.dp)) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(2.dp))
        Text(label)
    }
}

/** Content of an element chip: Text shows the text, Symbol the character, Frame a small box. */
@Composable
private fun ElementChipLabel(element: LabelElement) {
    when (element) {
        is TextElement -> Text(
            element.text.replace('\n', ' ').ifBlank { stringResource(R.string.add_text) },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 96.dp),
        )
        is IconElement -> Text(element.glyph, maxLines = 1)
        is FrameElement -> Box(
            Modifier
                .size(width = 22.dp, height = 13.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp))
        )
        is BarcodeElement -> Text(
            if (element.symbology == Symbology.QR_CODE) "QR" else "▊▎▊",
            maxLines = 1
        )
        is ImageElement -> Text(stringResource(R.string.add_image), maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PropertiesPanel(
    element: LabelElement,
    onUpdate: (LabelElement) -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        when (element) {
            is TextElement -> TextProperties(element, onUpdate)
            is IconElement -> IconProperties(element, onUpdate)
            is FrameElement -> FrameProperties(element, onUpdate)
            is BarcodeElement -> BarcodeProperties(element, onUpdate)
            is ImageElement -> ImageProperties(element, onUpdate)
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column {
                GroupLabel(stringResource(R.string.cd_rotate))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Fine 15° steps plus a quick 90° jump.
                    Stepper(
                        label = "",
                        value = "${element.rotation}°",
                        onDecrease = { onUpdate(element.withRotation((element.rotation - 15 + 360) % 360)) },
                        onIncrease = { onUpdate(element.withRotation((element.rotation + 15) % 360)) },
                    )
                    OutlinedButton(
                        onClick = { onUpdate(element.withRotation((element.rotation + 90) % 360)) },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_rotate_cw),
                            contentDescription = stringResource(R.string.cd_rotate),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("90°")
                    }
                }
            }
            Column {
                GroupLabel(stringResource(R.string.group_scale))
                val pct = (LabelRenderer.measure(element).height / LabelSpec.PRINT_HEIGHT_PX * 100f)
                    .roundToInt().coerceIn(1, 999)
                // Codes cap at 100 % (their box must fit the printable height to stay scannable);
                // everything else scales up to 200 %.
                val scaleMax = if (element is BarcodeElement) 100 else 200
                Stepper(
                    label = "",
                    value = "$pct %",
                    onDecrease = { onUpdate(element.scaledToHeightPercent((pct - 1).coerceAtLeast(2))) },
                    onIncrease = { onUpdate(element.scaledToHeightPercent((pct + 1).coerceAtMost(scaleMax))) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onDelete,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.menu_delete))
        }
    }
}

private fun LabelElement.withRotation(deg: Int): LabelElement = when (this) {
    is TextElement -> copy(rotation = deg)
    is IconElement -> copy(rotation = deg)
    is FrameElement -> copy(rotation = deg)
    is BarcodeElement -> copy(rotation = deg)
    is ImageElement -> copy(rotation = deg)
}

/** Scales the element so its height becomes pct % of the label height; width proportional. */
private fun LabelElement.scaledToHeightPercent(pct: Int): LabelElement {
    val target = pct / 100f * LabelSpec.PRINT_HEIGHT_PX
    val current = LabelRenderer.measure(this).height
    val factor = if (current > 0.1f) target / current else 1f
    // Allow up to 200 % (bigger than the label) so an element can be zoomed/cropped.
    val maxH = 2f * LabelSpec.PRINT_HEIGHT_PX
    return when (this) {
        is TextElement -> copy(
            fontSizePx = (fontSizePx * factor).coerceIn(6f, 200f),
            boxWidthPx = boxWidthPx?.let { it * factor },
        )
        is IconElement -> copy(sizePx = target.coerceIn(8f, maxH))
        is FrameElement -> copy(
            heightPx = target.coerceIn(2f, maxH),
            widthPx = (widthPx * factor).coerceAtLeast(2f),
        )
        is BarcodeElement -> {
            // Scale the reserved box like an image (keep aspect); the code re-fits and centers inside.
            // Capped at the label height so the printed code stays within the printable area.
            val h = target.coerceIn(16f, LabelSpec.PRINT_HEIGHT_PX.toFloat())
            val f = if (current > 0.1f) h / current else 1f
            copy(heightPx = h, widthPx = (widthPx * f).coerceAtLeast(16f))
        }
        is ImageElement -> copy(widthPx = (widthPx * factor).coerceAtLeast(8f))
    }
}

@Composable
private fun Stepper(label: String, value: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.bodyMedium)
        StepButton("-", onDecrease)
        Text(value, style = MaterialTheme.typography.bodyMedium)
        StepButton("+", onIncrease)
    }
}

/**
 * Plus/minus area: a short tap = one step; holding repeats and accelerates
 * (the interval gets shorter) until released.
 */
@Composable
private fun StepButton(symbol: String, onStep: () -> Unit) {
    val latest by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier
            .size(44.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val job = scope.launch {
                        latest()
                        delay(380)
                        var interval = 150L
                        while (true) {
                            latest()
                            delay(interval)
                            interval = (interval * 80 / 100).coerceAtLeast(35L)
                        }
                    }
                    waitForUpOrCancellation()
                    job.cancel()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge)
    }
}

/** Standalone yes/no option as a switch, visually distinct from the selection chips. */
@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Compact selectable chip: less horizontal padding than the stock FilterChip, so more fit per row. */
@Composable
private fun ChoiceChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier.heightIn(min = 30.dp).padding(horizontal = 8.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge, label)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BarcodeProperties(element: BarcodeElement, onUpdate: (LabelElement) -> Unit) {
    GroupLabel(stringResource(R.string.prop_barcode_type))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Symbology.entries.forEach { s ->
            ChoiceChip(
                selected = element.symbology == s,
                onClick = {
                    // Leaving QR for a 1D barcode: reset the wizard to raw text, and if we were on a
                    // structured payload (WiFi, contact, ...) keep only its primary value so the barcode
                    // does not carry a full WIFI:/MECARD: string.
                    val leavingStructured = element.symbology == Symbology.QR_CODE &&
                        element.payloadType != QrPayloadType.TEXT && element.payloadType != QrPayloadType.LINK
                    val type = if (s == Symbology.QR_CODE) element.payloadType else QrPayloadType.TEXT
                    val data = if (s != Symbology.QR_CODE && leavingStructured)
                        element.payload[QrPayload.primaryKey(element.payloadType)].orEmpty()
                    else element.data
                    onUpdate(element.copy(symbology = s, payloadType = type, data = data))
                },
                label = { Text(symbologyLabel(s)) },
            )
        }
    }
    if (element.symbology == Symbology.QR_CODE) {
        Spacer(Modifier.height(6.dp))
        GroupLabel(stringResource(R.string.qr_content))
        val types = listOf(
            QrPayloadType.TEXT to R.string.qr_type_text,
            QrPayloadType.LINK to R.string.qr_type_link,
            QrPayloadType.WIFI to R.string.qr_type_wifi,
            QrPayloadType.EMAIL to R.string.qr_type_email,
            QrPayloadType.PHONE to R.string.qr_type_phone,
            QrPayloadType.CONTACT to R.string.qr_type_contact,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            types.forEach { (type, label) ->
                ChoiceChip(
                    selected = element.payloadType == type,
                    onClick = { onUpdate(QrPayload.switchType(element, type)) },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        QrPayloadFields(element, onUpdate)
    } else {
        // 1D barcode: raw content plus the optional human-readable caption.
        OutlinedTextField(
            value = element.data,
            onValueChange = { onUpdate(element.copy(data = it)) },
            label = { Text(stringResource(R.string.prop_barcode_data)) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { if (element.data.isNotEmpty()) ClearButton { onUpdate(element.copy(data = "")) } },
            singleLine = true,
        )
        Spacer(Modifier.height(4.dp))
        ToggleRow(stringResource(R.string.prop_barcode_caption), element.showText) {
            onUpdate(element.copy(showText = it))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QrPayloadFields(element: BarcodeElement, onUpdate: (LabelElement) -> Unit) {
    // Set one or more payload fields and rebuild the encoded string the scanner reads.
    fun set(vararg pairs: Pair<String, String>) {
        val fields = element.payload + pairs
        onUpdate(element.copy(payload = fields, data = QrPayload.build(element.payloadType, fields)))
    }
    fun get(key: String) = element.payload[key].orEmpty()

    @Composable
    fun field(value: String, labelRes: Int, keyboardType: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(stringResource(labelRes)) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { if (value.isNotEmpty()) ClearButton { onChange("") } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
    }

    when (element.payloadType) {
        QrPayloadType.TEXT ->
            field(element.data, R.string.prop_barcode_data) { set(QrPayload.TEXT to it) }
        QrPayloadType.LINK -> {
            val url = get(QrPayload.URL)
            // TextFieldValue so the cursor can be placed at the end after the https:// prefill.
            var tfv by remember { mutableStateOf(TextFieldValue(url, TextRange(url.length))) }
            if (tfv.text != url) tfv = TextFieldValue(url, TextRange(url.length)) // sync external changes
            OutlinedTextField(
                value = tfv,
                onValueChange = { tfv = it; set(QrPayload.URL to it.text) },
                label = { Text(stringResource(R.string.qr_url)) },
                modifier = Modifier
                    .fillMaxWidth()
                    // Prefill https:// only once the empty field is tapped (cursor at the end), so it
                    // is never left as a stray default.
                    .onFocusChanged {
                        if (it.isFocused && get(QrPayload.URL).isEmpty()) {
                            set(QrPayload.URL to "https://")
                            tfv = TextFieldValue("https://", TextRange("https://".length))
                        }
                    },
                trailingIcon = { if (url.isNotEmpty()) ClearButton { set(QrPayload.URL to "") } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        }
        QrPayloadType.WIFI -> {
            field(get(QrPayload.SSID), R.string.qr_ssid) { set(QrPayload.SSID to it) }
            Spacer(Modifier.height(6.dp))
            GroupLabel(stringResource(R.string.qr_auth))
            val auths = listOf("WPA" to "WPA/WPA2/WPA3", "WEP" to "WEP", "nopass" to stringResource(R.string.qr_auth_none))
            val currentAuth = get(QrPayload.AUTH).ifBlank { "WPA" }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                auths.forEach { (value, label) ->
                    ChoiceChip(
                        selected = currentAuth == value,
                        onClick = { set(QrPayload.AUTH to value) },
                        label = { Text(label) },
                    )
                }
            }
            // The password applies only to encrypted networks; an open one has none.
            if (currentAuth != "nopass") {
                Spacer(Modifier.height(4.dp))
                var reveal by remember { mutableStateOf(false) }
                val password = get(QrPayload.PASSWORD)
                // Validate live: any non-empty password that is too short/long shows the error, which
                // also covers reopening an element whose stored password is invalid.
                val invalid = password.isNotEmpty() && !QrPayload.isWifiPasswordValid(currentAuth, password)
                OutlinedTextField(
                    value = password,
                    onValueChange = { set(QrPayload.PASSWORD to it) },
                    label = { Text(stringResource(R.string.qr_password)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = invalid,
                    // Plain text keyboard (not Password) so a password manager does not offer to save it.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { reveal = !reveal }) {
                            Icon(
                                painterResource(if (reveal) R.drawable.ic_eye_off else R.drawable.ic_eye),
                                contentDescription = stringResource(R.string.qr_show_password),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    supportingText = {
                        if (invalid) {
                            Text(
                                stringResource(if (currentAuth == "WEP") R.string.qr_wifi_pw_error_wep else R.string.qr_wifi_pw_error_wpa),
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Text(stringResource(R.string.qr_password_hint))
                        }
                    },
                )
            }
            Spacer(Modifier.height(4.dp))
            ToggleRow(stringResource(R.string.qr_hidden), get(QrPayload.HIDDEN) == "true") {
                set(QrPayload.HIDDEN to it.toString())
            }
        }
        QrPayloadType.EMAIL -> {
            field(get(QrPayload.EMAIL), R.string.qr_email_addr, KeyboardType.Email) { set(QrPayload.EMAIL to it) }
            Spacer(Modifier.height(4.dp))
            field(get(QrPayload.SUBJECT), R.string.qr_subject) { set(QrPayload.SUBJECT to it) }
        }
        QrPayloadType.PHONE ->
            field(get(QrPayload.PHONE), R.string.qr_phone, KeyboardType.Phone) { set(QrPayload.PHONE to it) }
        QrPayloadType.CONTACT -> {
            field(get(QrPayload.NAME), R.string.field_name) { set(QrPayload.NAME to it) }
            Spacer(Modifier.height(4.dp))
            field(get(QrPayload.PHONE), R.string.qr_phone, KeyboardType.Phone) { set(QrPayload.PHONE to it) }
            Spacer(Modifier.height(4.dp))
            field(get(QrPayload.EMAIL), R.string.qr_email_addr, KeyboardType.Email) { set(QrPayload.EMAIL to it) }
        }
    }
}

private fun symbologyLabel(s: Symbology): String = when (s) {
    Symbology.QR_CODE -> "QR"
    Symbology.CODE_128 -> "Code 128"
    Symbology.EAN_13 -> "EAN-13"
    Symbology.UPC_A -> "UPC-A"
    Symbology.CODE_39 -> "Code 39"
    Symbology.ITF -> "ITF"
}

/**
 * Style, Smooth and Invert side by side, each with its own heading (like the Rotate/Scale row).
 * Shared by icons and images; shown in outline mode.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OutlineOptionsRow(
    method: OutlineMethod,
    smooth: Boolean,
    invert: Boolean,
    onMethod: (OutlineMethod) -> Unit,
    onSmooth: (Boolean) -> Unit,
    onInvert: (Boolean) -> Unit,
) {
    val options = listOf(
        OutlineMethod.LINES to R.string.outline_lines,
        OutlineMethod.CANNY to R.string.outline_canny,
    )
    Spacer(Modifier.height(6.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column {
            GroupLabel(stringResource(R.string.prop_outline_style))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { (m, label) ->
                    ChoiceChip(selected = method == m, onClick = { onMethod(m) }, label = { Text(stringResource(label)) })
                }
            }
        }
        Column {
            GroupLabel(stringResource(R.string.outline_smooth))
            Switch(checked = smooth, onCheckedChange = onSmooth)
        }
        Column {
            GroupLabel(stringResource(R.string.prop_invert))
            Switch(checked = invert, onCheckedChange = onInvert)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageProperties(element: ImageElement, onUpdate: (LabelElement) -> Unit) {
    GroupLabel(stringResource(R.string.group_raster))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val modes = listOf(
            DitherMode.OUTLINE to R.string.dither_outline,
            DitherMode.THRESHOLD to R.string.dither_threshold,
            DitherMode.FLOYD_STEINBERG to R.string.dither_fs,
            DitherMode.ATKINSON to R.string.dither_atkinson,
        )
        modes.forEach { (mode, label) ->
            ChoiceChip(
                selected = element.dither == mode,
                onClick = { onUpdate(element.copy(dither = mode)) },
                label = { Text(stringResource(label)) },
            )
        }
    }
    when (element.dither) {
        DitherMode.OUTLINE -> {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.prop_outline_detail) + ": ${element.outlineSensitivity}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = element.outlineSensitivity.toFloat(),
                onValueChange = { onUpdate(element.copy(outlineSensitivity = it.roundToInt())) },
                valueRange = 0f..100f,
            )
            Stepper(
                label = stringResource(R.string.prop_line_width) + ": ",
                value = "${element.outlineThickness} px",
                onDecrease = { onUpdate(element.copy(outlineThickness = (element.outlineThickness - 1).coerceAtLeast(1))) },
                onIncrease = { onUpdate(element.copy(outlineThickness = (element.outlineThickness + 1).coerceAtMost(3))) },
            )
            OutlineOptionsRow(
                method = element.outlineMethod,
                smooth = element.outlineSmooth,
                invert = element.invert,
                onMethod = { onUpdate(element.copy(outlineMethod = it)) },
                onSmooth = { onUpdate(element.copy(outlineSmooth = it)) },
                onInvert = { onUpdate(element.copy(invert = it)) },
            )
        }
        DitherMode.THRESHOLD -> {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.prop_image_threshold) + ": ${element.threshold}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = element.threshold.toFloat(),
                onValueChange = { onUpdate(element.copy(threshold = it.toInt())) },
                valueRange = 20f..235f,
            )
        }
        else -> {
            // Floyd-Steinberg / Atkinson: contrast tunes the tones before dithering.
            Spacer(Modifier.height(6.dp))
            GroupLabel(stringResource(R.string.prop_contrast) + ": ${element.contrast}")
            Slider(
                value = element.contrast.toFloat(),
                onValueChange = { onUpdate(element.copy(contrast = it.roundToInt())) },
                valueRange = -100f..100f,
            )
        }
    }
    if (element.dither != DitherMode.OUTLINE) {
        Spacer(Modifier.height(4.dp))
        ToggleRow(stringResource(R.string.prop_invert), element.invert) { onUpdate(element.copy(invert = it)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextProperties(element: TextElement, onUpdate: (LabelElement) -> Unit) {
    OutlinedTextField(
        value = element.text,
        onValueChange = { onUpdate(element.copy(text = it)) },
        label = { Text(stringResource(R.string.prop_text)) },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = { if (element.text.isNotEmpty()) ClearButton { onUpdate(element.copy(text = "")) } },
        minLines = 1,
        maxLines = 4
    )
    Spacer(Modifier.height(4.dp))
    Stepper(
        label = stringResource(R.string.prop_size) + ": ",
        value = "${element.fontSizePx.toInt()} px",
        onDecrease = { onUpdate(element.copy(fontSizePx = (element.fontSizePx - 4).coerceAtLeast(8f))) },
        onIncrease = { onUpdate(element.copy(fontSizePx = (element.fontSizePx + 4).coerceAtMost(96f))) }
    )

    Spacer(Modifier.height(6.dp))
    GroupLabel(stringResource(R.string.group_font))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LabelFont.entries.forEach { f ->
            ChoiceChip(
                selected = element.font == f,
                onClick = { onUpdate(element.copy(font = f)) },
                label = {
                    Text(
                        when (f) {
                            LabelFont.SANS -> stringResource(R.string.font_sans)
                            LabelFont.SERIF -> stringResource(R.string.font_serif)
                            LabelFont.MONO -> stringResource(R.string.font_mono)
                            LabelFont.OSWALD -> "Oswald"
                            LabelFont.ZILLA_SLAB -> "Slab"
                            LabelFont.COMFORTAA -> "Rund"
                            LabelFont.CAVEAT -> "Caveat"
                            LabelFont.PACIFICO -> "Pacifico"
                        },
                        maxLines = 1,
                        softWrap = false
                    )
                }
            )
        }
    }

    Spacer(Modifier.height(6.dp))
    GroupLabel(stringResource(R.string.group_format))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ChoiceChip(
            selected = element.bold,
            onClick = { onUpdate(element.copy(bold = !element.bold)) },
            label = { Text(stringResource(R.string.prop_bold)) })
        ChoiceChip(
            selected = element.italic,
            onClick = { onUpdate(element.copy(italic = !element.italic)) },
            label = { Text(stringResource(R.string.prop_italic)) })
        ChoiceChip(
            selected = element.underline,
            onClick = { onUpdate(element.copy(underline = !element.underline)) },
            label = { Text(stringResource(R.string.prop_underline)) })
    }

    Spacer(Modifier.height(6.dp))
    GroupLabel(stringResource(R.string.group_align))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LabelTextAlign.entries.forEach { align ->
            ChoiceChip(
                selected = element.align == align,
                onClick = { onUpdate(element.copy(align = align)) },
                label = {
                    Text(
                        when (align) {
                            LabelTextAlign.LEFT -> stringResource(R.string.align_left)
                            LabelTextAlign.CENTER -> stringResource(R.string.align_center)
                            LabelTextAlign.RIGHT -> stringResource(R.string.align_right)
                        }
                    )
                }
            )
        }
    }

    Spacer(Modifier.height(6.dp))
    GroupLabel(stringResource(R.string.group_variables))
    val tokens = listOf(
        stringResource(R.string.var_date) to "{date}",
        stringResource(R.string.var_time) to "{time}",
        stringResource(R.string.var_number) to "{#}",
        stringResource(R.string.var_var) to "{var:Text}",
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tokens.forEach { (label, token) ->
            ChoiceChip(
                selected = false,
                onClick = { onUpdate(element.copy(text = element.text + token)) },
                label = { Text(label) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconProperties(element: IconElement, onUpdate: (LabelElement) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    GroupLabel(stringResource(R.string.symbol_current))
    Box(
        modifier = Modifier
            .size(72.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable { showPicker = true },
        contentAlignment = Alignment.Center
    ) {
        Text(element.glyph, fontSize = 40.sp)
    }
    Spacer(Modifier.height(8.dp))
    Stepper(
        label = stringResource(R.string.prop_size) + ": ",
        value = "${element.sizePx.toInt()} px",
        onDecrease = { onUpdate(element.copy(sizePx = (element.sizePx - 8).coerceAtLeast(16f))) },
        onIncrease = { onUpdate(element.copy(sizePx = (element.sizePx + 8).coerceAtMost(96f))) }
    )
    Spacer(Modifier.height(6.dp))
    GroupLabel(stringResource(R.string.group_raster))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DitherMode.entries.forEach { mode ->
            ChoiceChip(
                selected = element.dither == mode,
                onClick = { onUpdate(element.copy(dither = mode)) },
                label = {
                    Text(
                        when (mode) {
                            DitherMode.THRESHOLD -> stringResource(R.string.dither_threshold)
                            DitherMode.FLOYD_STEINBERG -> stringResource(R.string.dither_fs)
                            DitherMode.ATKINSON -> stringResource(R.string.dither_atkinson)
                            DitherMode.OUTLINE -> stringResource(R.string.dither_outline)
                        }
                    )
                }
            )
        }
    }
    Text(
        stringResource(R.string.raster_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp)
    )

    Spacer(Modifier.height(6.dp))
    // Outline mode only controls the number of bands; the contrast slider would interfere there
    // with the fixed quantization (outlines flicker) and is therefore omitted. Otherwise: contrast.
    if (element.dither == DitherMode.OUTLINE) {
        GroupLabel(stringResource(R.string.prop_outline_detail) + ": ${element.outlineSensitivity}")
        Slider(
            value = element.outlineSensitivity.toFloat(),
            onValueChange = { onUpdate(element.copy(outlineSensitivity = it.roundToInt())) },
            valueRange = 0f..100f
        )
        Spacer(Modifier.height(6.dp))
        Stepper(
            label = stringResource(R.string.prop_line_width) + ": ",
            value = "${element.outlineThickness} px",
            onDecrease = { onUpdate(element.copy(outlineThickness = (element.outlineThickness - 1).coerceAtLeast(1))) },
            onIncrease = { onUpdate(element.copy(outlineThickness = (element.outlineThickness + 1).coerceAtMost(3))) }
        )
        OutlineOptionsRow(
            method = element.outlineMethod,
            smooth = element.outlineSmooth,
            invert = element.invert,
            onMethod = { onUpdate(element.copy(outlineMethod = it)) },
            onSmooth = { onUpdate(element.copy(outlineSmooth = it)) },
            onInvert = { onUpdate(element.copy(invert = it)) },
        )
    } else {
        GroupLabel(stringResource(R.string.prop_contrast) + ": ${element.contrast}")
        Slider(
            value = element.contrast.toFloat(),
            onValueChange = { onUpdate(element.copy(contrast = it.roundToInt())) },
            valueRange = -100f..100f
        )
    }
    if (element.dither != DitherMode.OUTLINE) {
        Spacer(Modifier.height(4.dp))
        ToggleRow(stringResource(R.string.prop_invert), element.invert) { onUpdate(element.copy(invert = it)) }
    }

    if (showPicker) {
        SymbolPickerSheet(
            onPick = { glyph, isEmoji ->
                // Set the default dither only on first assignment (still the placeholder glyph):
                // emoji -> outline, single-color symbols -> threshold. On a later change the choice stays.
                val newDither = if (element.glyph == "□") {
                    if (isEmoji) DitherMode.OUTLINE else DitherMode.THRESHOLD
                } else {
                    element.dither
                }
                onUpdate(element.copy(glyph = glyph, dither = newDither))
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun FrameProperties(element: FrameElement, onUpdate: (LabelElement) -> Unit) {
    val rectSelected = element.style == FrameStyle.RECT || element.style == FrameStyle.ROUND_RECT
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ChoiceChip(
            selected = rectSelected,
            onClick = { onUpdate(element.copy(style = FrameStyle.RECT)) },
            label = { Text(stringResource(R.string.frame_rect)) }
        )
        ChoiceChip(
            selected = element.style == FrameStyle.LINE_H,
            onClick = { onUpdate(element.copy(style = FrameStyle.LINE_H)) },
            label = { Text(stringResource(R.string.frame_line_h)) }
        )
        ChoiceChip(
            selected = element.style == FrameStyle.LINE_V,
            onClick = { onUpdate(element.copy(style = FrameStyle.LINE_V)) },
            label = { Text(stringResource(R.string.frame_line_v)) }
        )
    }
    Spacer(Modifier.height(4.dp))
    Stepper(
        label = stringResource(R.string.prop_stroke) + ": ",
        value = "${element.strokePx.toInt()} px",
        onDecrease = { onUpdate(element.copy(strokePx = (element.strokePx - 1).coerceAtLeast(1f))) },
        onIncrease = { onUpdate(element.copy(strokePx = (element.strokePx + 1).coerceAtMost(10f))) }
    )
    if (rectSelected) {
        Stepper(
            label = stringResource(R.string.prop_radius) + ": ",
            value = "${element.cornerRadiusPx.toInt()} px",
            onDecrease = { onUpdate(element.copy(cornerRadiusPx = (element.cornerRadiusPx - 2).coerceAtLeast(0f))) },
            onIncrease = { onUpdate(element.copy(cornerRadiusPx = (element.cornerRadiusPx + 2).coerceAtMost(48f))) }
        )
    }
    Stepper(
        label = stringResource(R.string.prop_width) + ": ",
        value = "${element.widthPx.toInt()} px",
        onDecrease = { onUpdate(element.copy(widthPx = (element.widthPx - 8).coerceAtLeast(8f))) },
        onIncrease = { onUpdate(element.copy(widthPx = element.widthPx + 8)) }
    )
    Stepper(
        label = stringResource(R.string.prop_height) + ": ",
        value = "${element.heightPx.toInt()} px",
        onDecrease = { onUpdate(element.copy(heightPx = (element.heightPx - 8).coerceAtLeast(8f))) },
        onIncrease = { onUpdate(element.copy(heightPx = (element.heightPx + 8).coerceAtMost(96f))) }
    )
}
