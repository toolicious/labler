package io.github.toolicious.labler.ui.editor

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.toolicious.labler.App
import io.github.toolicious.labler.R
import io.github.toolicious.labler.data.CaptionFont
import io.github.toolicious.labler.data.CustomFontRepository
import io.github.toolicious.labler.model.BarcodeElement
import io.github.toolicious.labler.model.FrameElement
import io.github.toolicious.labler.model.FrameStyle
import io.github.toolicious.labler.model.IconElement
import io.github.toolicious.labler.model.ImageElement
import io.github.toolicious.labler.model.LabelElement
import io.github.toolicious.labler.model.LabelFont
import io.github.toolicious.labler.printer.HeadGeometry
import io.github.toolicious.labler.model.LabelSpec
import io.github.toolicious.labler.model.LabelTextAlign
import io.github.toolicious.labler.model.QrPayload
import io.github.toolicious.labler.model.QrPayloadType
import io.github.toolicious.labler.model.Symbology
import io.github.toolicious.labler.model.TextElement
import io.github.toolicious.labler.printer.dither.DitherMode
import io.github.toolicious.labler.printer.dither.OutlineMethod
import io.github.toolicious.labler.render.FontRegistry
import io.github.toolicious.labler.render.LabelRenderer
import io.github.toolicious.labler.render.PixelFonts
import io.github.toolicious.labler.ui.components.ClearButton
import io.github.toolicious.labler.ui.components.appDateFormat
import io.github.toolicious.labler.ui.components.appTimeFormat
import io.github.toolicious.labler.ui.components.systemLocale
import io.github.toolicious.labler.ui.components.iconFontFamily
import io.github.toolicious.labler.ui.components.labelFontFamily
import io.github.toolicious.labler.ui.components.rememberBlePermissionRunner
import io.github.toolicious.labler.ui.home.LabelDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import io.github.toolicious.labler.ui.print.TemplatePrintSheet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    templateId: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenFonts: () -> Unit = {},
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
                        // Default to fit within the label height (no clipping), so the box matches
                        // the image. The lower bound is capped so a very tall/narrow image cannot
                        // exceed the head. In reference dots; the view model scales it to this
                        // label's head, like every other element default.
                        widthPx = run {
                            val head = LabelSpec.DEFAULT_ELEMENT_HEAD_DOTS.toFloat()
                            val ratio = loaded.width.toFloat() / loaded.height
                            (76f * ratio).coerceIn(minOf(16f, head * ratio), 480f)
                        },
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
                        overflow = TextOverflow.Ellipsis,
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
                onEdgeDragStart = vm::beginEdgeDrag,
                onEdgeDragBy = vm::dragEdgeBy,
                onEdgeDragEnd = vm::endEdgeDrag,
                onEdgeFit = vm::fitEdge,

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
            // The size doubles as the entry point to the label dialog (name + size). The title in the
            // top bar opens the same dialog, but a plain title does not look tappable, so the size
            // is set in the accent color to make the option visible at all.
            // On a variable label the length only exists as a result, so it is shown live next to
            // the setting. Remembered because measuring every element is not free and this recomposes
            // on each edit. It is the design length: placeholders resolve only in the print sheet.
            val autoLengthMm = remember(t.spec, t.elements, FontRegistry.revision) {
                if (t.spec.lengthIsAuto) LabelRenderer.effectiveLengthMm(t.spec, t.elements) else 0
            }
            Text(
                if (t.spec.lengthIsAuto) {
                    stringResource(R.string.template_size_auto, t.spec.tapeWidthMm, autoLengthMm)
                } else {
                    stringResource(R.string.template_size, t.spec.tapeWidthMm, t.spec.lengthMm)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = stringResource(R.string.dialog_edit_title)) {
                        showMetaDialog = true
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )

            GroupLabel(stringResource(R.string.group_add))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AddButton(stringResource(R.string.add_text)) {
                    vm.addElement(TextElement(id = UUID.randomUUID().toString()))
                }
                AddButton(stringResource(R.string.add_symbol)) {
                    vm.addElement(IconElement(id = UUID.randomUUID().toString()))
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
                        // A frame spanning the whole label. On an auto-length tape "whole" can
                        // only mean the length the content has right now; from then on the frame
                        // itself is a fixed element and holds that length.
                        FrameElement(
                            id = id,
                            x = vm.tapeStartPx + 2f, y = 2f,
                            widthPx = (LabelRenderer.effectiveLengthPx(t.spec, t.elements) - 4).toFloat(),
                            heightPx = (t.spec.printHeightPx - 4).toFloat()
                        )
                    }
                    // Placed already: it either wraps the selected element or spans the tape.
                    vm.addElement(frame, place = false)
                }
                AddButton(stringResource(R.string.add_barcode)) {
                    vm.addElement(
                        BarcodeElement(
                            id = UUID.randomUUID().toString(),
                            captionFont = lastCaptionFont.font,
                            captionCustomFont = lastCaptionFont.custom,
                        )
                    )
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
                        geometry = t.spec.geometry,
                        onUpdate = vm::updateElement,
                        onDelete = vm::deleteSelected,
                        onOpenFonts = onOpenFonts
                    )
                } ?: Text(
                    // Blank line between the two hints, so the gesture stands out as its own note.
                    stringResource(R.string.editor_hint) + "\n\n" +
                        stringResource(R.string.editor_hint_free_move),
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
            currentLengthMm = LabelRenderer.effectiveLengthMm(t.spec, t.elements),
        )
    }
}

@Composable
private fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = 4.dp)
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
        is IconElement -> Text(element.glyph, fontFamily = iconFontFamily(element.iconFont), maxLines = 1)
        is FrameElement -> Box(
            Modifier
                .size(width = 22.dp, height = 13.dp)
                .border(1.5.dp, LocalContentColor.current, RoundedCornerShape(2.dp))
        )
        is BarcodeElement -> Text(
            if (element.symbology.isMatrix) symbologyLabel(element.symbology) else "▊▎▊",
            maxLines = 1
        )
        is ImageElement -> Text(
            stringResource(R.string.add_image),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PropertiesPanel(
    element: LabelElement,
    geometry: HeadGeometry,
    onUpdate: (LabelElement) -> Unit,
    onDelete: () -> Unit,
    onOpenFonts: () -> Unit,
) {
    Column {
        when (element) {
            is TextElement -> TextProperties(element, onUpdate, onOpenFonts)
            is IconElement -> IconProperties(element, geometry, onUpdate)
            is FrameElement -> FrameProperties(element, geometry, onUpdate)
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
                        edit = NumberEdit(
                            title = stringResource(R.string.cd_rotate),
                            value = element.rotation,
                            range = 0..359,
                            onValue = { onUpdate(element.withRotation(it)) },
                        ),
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
                val pct = (LabelRenderer.measure(element).height / geometry.headDots * 100f)
                    .roundToInt().coerceIn(1, 999)
                // Codes cap at 100 % (their box must fit the printable height to stay scannable);
                // everything else scales up to 200 %.
                val scaleMax = if (element is BarcodeElement) 100 else 200
                Stepper(
                    label = "",
                    value = "$pct %",
                    onDecrease = { onUpdate(element.scaledToHeightPercent((pct - 1).coerceAtLeast(2), geometry)) },
                    onIncrease = { onUpdate(element.scaledToHeightPercent((pct + 1).coerceAtMost(scaleMax), geometry)) },
                    edit = NumberEdit(
                        title = stringResource(R.string.group_scale),
                        value = pct,
                        range = 2..scaleMax,
                        onValue = { onUpdate(element.scaledToHeightPercent(it, geometry)) },
                    ),
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
            Text(stringResource(R.string.menu_delete_prefix))
            Spacer(Modifier.width(4.dp))
            // The very preview the element carries in its chip above, so what is about to go is
            // named rather than left to be remembered.
            ElementChipLabel(element)
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
/**
 * How large an element may be made. Twice the printable height, so it can be blown up and cropped
 * rather than only ever fitted, which is the same thing the label edges allow. Text goes by its
 * font size instead of its height and carries its own number.
 *
 * The size fields and the scale control both work to these, or the one would stop where the other
 * still went on.
 */
private fun maxElementHeightPx(geometry: HeadGeometry): Int = 2 * geometry.headDots
private const val MAX_FONT_SIZE_PX = 200

private fun LabelElement.scaledToHeightPercent(pct: Int, geometry: HeadGeometry): LabelElement {
    val target = pct / 100f * geometry.headDots
    val current = LabelRenderer.measure(this).height
    val factor = if (current > 0.1f) target / current else 1f
    val maxH = maxElementHeightPx(geometry).toFloat()
    return when (this) {
        is TextElement -> copy(
            fontSizePx = (fontSizePx * factor).coerceIn(6f, MAX_FONT_SIZE_PX.toFloat()),
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
            val h = target.coerceIn(16f, geometry.headDots.toFloat())
            val f = if (current > 0.1f) h / current else 1f
            copy(heightPx = h, widthPx = (widthPx * f).coerceAtLeast(16f))
        }
        is ImageElement -> copy(widthPx = (widthPx * factor).coerceAtLeast(8f))
    }
}

/**
 * What a tap on a stepper number offers to type in: the number that is there, the bounds it has to
 * keep to, and where the result goes. [title] names the dialog, because the label beside a number
 * is not always there to say what it is.
 */
private class NumberEdit(
    val title: String,
    val value: Int,
    val range: IntRange,
    val onValue: (Int) -> Unit,
)

/**
 * @param edit what a tap on the number opens. Stepping through a range with the buttons is fine
 *   for a nudge and tedious for a jump, so the number itself takes a value straight away.
 */
@Composable
private fun Stepper(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    edit: NumberEdit? = null,
    /**
     * The longest readings this stepper can show, for one that has to keep still beside something
     * else. Laid out rather than guessed at in dp, so it also covers a translation longer than the
     * one it was written against.
     */
    readings: List<String> = emptyList(),
) {
    var typing by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.bodyMedium)
        StepButton("-", onDecrease)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .then(if (edit == null) Modifier else Modifier.clickable { typing = true })
                // As tall as the two buttons beside it, so the number is as easy to hit as they are
                // and the row does not change height for it.
                .heightIn(min = 44.dp)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Every reading laid out invisibly underneath, so the box is exactly as wide as the
            // longest of them will ever need and the row stops shifting as the number gains a
            // digit. Nothing to read out, hence the cleared semantics.
            readings.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.alpha(0f).clearAndSetSemantics {},
                )
            }
            Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        StepButton("+", onIncrease)
    }
    if (typing && edit != null) {
        NumberDialog(edit) { typing = false }
    }
}

/** Types a number into a stepper. Opens with the current one filled in and selected. */
@Composable
private fun NumberDialog(edit: NumberEdit, onDismiss: () -> Unit) {
    val current = edit.value.toString()
    var text by remember { mutableStateOf(TextFieldValue(current, TextRange(0, current.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val commit = {
        // An empty or unreadable entry leaves the value alone rather than guessing at one.
        text.text.trim().toIntOrNull()?.let { edit.onValue(it.coerceIn(edit.range)) }
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(edit.title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                supportingText = { Text("${edit.range.first} - ${edit.range.last}") },
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = commit) { Text(stringResource(R.string.action_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
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

/**
 * Font last picked for a bar code caption. Process-wide cache, loaded from the settings at app
 * start and written back on every pick, the same way the symbol picker keeps its tab, so the next
 * code element starts out with the font the last one got.
 */
internal var lastCaptionFont = CaptionFont()

/** Custom fonts, so a property panel can offer them next to the built-in ones. */
@Composable
private fun rememberFontRepository(): CustomFontRepository {
    val context = LocalContext.current
    return remember(context) { (context.applicationContext as App).container.customFonts }
}

/**
 * Chip caption set in the font it selects, capped in width so that one long family name cannot
 * push the whole row out of shape.
 */
/**
 * Chip caption drawn out of the face's own dots, for the bitmap fonts.
 *
 * Every family is sampled at the same dot size, so the captions come out as different in height as
 * the faces are: Fixed, seven rows tall, ends up visibly smaller than Terminus at twelve. That is
 * the point of it, the chip has to show which one belongs on tiny text.
 */
@Composable
private fun PixelFontChipLabel(font: LabelFont, text: String) {
    val density = LocalDensity.current
    val line = fontChipLineHeight()
    val sample = remember(font, text, density.density, line) {
        PixelFonts.sample(font, with(density) { line.toPx() })?.rasterize(text)?.asImageBitmap()
    }
    if (sample == null) {
        FontChipLabel(text, null)
        return
    }
    Row(modifier = Modifier.height(line), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_font_pixel),
            contentDescription = null,
            modifier = Modifier.size(FONT_CHIP_ICON),
        )
        Spacer(Modifier.width(4.dp))
        Image(
            bitmap = sample,
            contentDescription = text,
            // Unfiltered, or the dots go soft and the picture stops being the argument it is
            // meant to be.
            filterQuality = FilterQuality.None,
            colorFilter = ColorFilter.tint(LocalContentColor.current),
            modifier = Modifier.size(
                with(density) { sample.width.toDp() },
                with(density) { sample.height.toDp() },
            ),
        )
    }
}

/** What a built-in font is called. The bundled ones go by their own name. */
@Composable
private fun fontName(font: LabelFont): String = when (font) {
    LabelFont.SANS -> stringResource(R.string.font_sans)
    LabelFont.SERIF -> stringResource(R.string.font_serif)
    LabelFont.MONO -> stringResource(R.string.font_mono)
    LabelFont.OSWALD -> "Oswald"
    LabelFont.ZILLA_SLAB -> "Slab"
    LabelFont.COMFORTAA -> "Rund"
    LabelFont.CAVEAT -> "Caveat"
    LabelFont.PACIFICO -> "Pacifico"
    LabelFont.PIXEL_FIXED -> "Fixed"
    LabelFont.PIXEL_TERMINUS -> "Terminus"
}

/** A built-in font's name, set in that font. */
@Composable
private fun FontLabel(font: LabelFont) {
    val name = fontName(font)
    // A bitmap face has no Typeface to hand Compose, and a substitute would hide the one thing
    // that makes it worth picking.
    if (PixelFonts.isPixel(font)) PixelFontChipLabel(font, name)
    else FontChipLabel(name, labelFontFamily(font = font))
}

/**
 * The same for a font of the user's own. The icon tells the three kinds apart at a glance: plain
 * text for the built-in ones, dots for the bitmap ones, and this for the imported ones.
 */
@Composable
private fun CustomFontLabel(label: String, family: String) {
    Row(
        modifier = Modifier.height(fontChipLineHeight()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_font_custom),
            contentDescription = null,
            modifier = Modifier.size(FONT_CHIP_ICON),
        )
        Spacer(Modifier.width(4.dp))
        FontChipLabel(label, labelFontFamily(customFamily = family))
    }
}

@Composable
private fun FontChipLabel(text: String, fontFamily: FontFamily?) {
    Text(
        text,
        fontFamily = fontFamily,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 128.dp),
    )
}

/**
 * Outlined button for the right end of a section caption row. A stock OutlinedButton has a 40.dp
 * minimum height and would more than double that row, so this is sized off the caption's own text
 * style and adds only the border to it.
 */
@Composable
private fun SectionActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    // Deliberately not Surface(onClick), because Material 3 pads that out to the 48.dp minimum
    // touch target and would leave a wide gap between this row and whatever follows it. Modifier
    // .border draws inside the bounds, so the whole height is the caption line plus 2.dp.
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.primary, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

/** What minimumInteractiveComponentSize reserves around a chip, and what the chip fills out. */
private val TOUCH_TARGET = 48.dp

/** The mark that tells the three kinds of font in the chip row apart. */
private val FONT_CHIP_ICON = 13.dp

/**
 * One line of chip caption, as tall as the text in the plain chips beside it.
 *
 * Taken from the type scale rather than written down as a number: the captions are set in sp and
 * grow with the reader's font size, while a drawing is in dp and does not. Pinning the drawings to
 * this keeps every chip in the row the same height whatever that setting is.
 */
@Composable
private fun fontChipLineHeight(): Dp {
    val style = MaterialTheme.typography.labelLarge
    val height = style.lineHeight.takeOrElse { style.fontSize * 1.4f }
    return with(LocalDensity.current) { height.toDp() }
}

/**
 * Compact selectable chip: less horizontal padding than the stock FilterChip, so more fit per row.
 *
 * Every chip takes the same path, whether or not it has an [onLongClick], because two paths differed
 * in their geometry once already: the clickable Surface reserves the 48 dp touch target and the
 * plain one does not, which left the chips of one row sitting at different sizes. So the Surface is
 * always the plain one, the touch target is stated here, and the gesture sits on the content, which
 * the Surface clips to its shape so the ripple keeps to the rounded corners.
 */
@Composable
private fun ChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    error: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    label: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = Modifier.minimumInteractiveComponentSize(),
        shape = RoundedCornerShape(8.dp),
        color = when {
            error -> MaterialTheme.colorScheme.errorContainer
            selected -> MaterialTheme.colorScheme.secondaryContainer
            else -> Color.Transparent
        },
        contentColor = when {
            error -> MaterialTheme.colorScheme.onErrorContainer
            selected -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = when {
            error -> BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            selected -> null
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Box(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick?.let {
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            it()
                        }
                    },
                    role = Role.Button,
                    hapticFeedbackEnabled = false,
                )
                .heightIn(min = 30.dp)
                // The chip fills the touch target reserved around it instead of floating in the
                // middle of it. A short label like "#" otherwise leaves ten empty pixels on either
                // side that belong to the chip and read as spacing, which makes the gaps in a row
                // look uneven. It costs nothing in layout, that width was already taken.
                .widthIn(min = TOUCH_TARGET)
                .padding(horizontal = 8.dp, vertical = 5.dp),
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
    SymbologyPicker(element.symbology) { s ->
        // Leaving a matrix code for a 1D barcode: reset the wizard to raw text, and if we were on a
        // structured payload (WiFi, contact, ...) keep only its primary value so the barcode does
        // not carry a full WIFI:/MECARD: string.
        val leavingStructured = element.symbology.isMatrix &&
            element.payloadType != QrPayloadType.TEXT && element.payloadType != QrPayloadType.LINK
        val type = if (s.isMatrix) element.payloadType else QrPayloadType.TEXT
        val data = if (!s.isMatrix && leavingStructured)
            element.payload[QrPayload.primaryKey(element.payloadType)].orEmpty()
        else element.data
        onUpdate(element.copy(symbology = s, payloadType = type, data = data))
    }
    if (element.symbology.isMatrix) {
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
        // Six, like every other heading in this panel. Four was right when a switch stood here,
        // a heading sits further from what it follows.
        Spacer(Modifier.height(6.dp))
        val context = LocalContext.current
        val container = remember(context) { (context.applicationContext as App).container }
        GroupLabel(stringResource(R.string.prop_barcode_caption))
        // Switch, font and size on one row, with the heading above carrying the word that would
        // otherwise sit next to the switch and cost the font name its space. A Row and not a
        // FlowRow on purpose: the font picker gives way instead of anything dropping to a line
        // of its own.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(
                checked = element.showText,
                onCheckedChange = { onUpdate(element.copy(showText = it)) },
            )
            // Nothing to set a font or a size for while the caption is off.
            if (element.showText) {
                Spacer(Modifier.width(8.dp))
                CaptionFontPicker(
                    font = element.captionFont,
                    custom = element.captionCustomFont,
                    modifier = Modifier.weight(1f),
                ) { font, custom ->
                    onUpdate(element.copy(captionFont = font, captionCustomFont = custom))
                    lastCaptionFont = CaptionFont(font, custom)
                    container.applicationScope.launch {
                        container.settings.saveCaptionFont(lastCaptionFont)
                    }
                }
                CaptionSizeSpinner(element) { onUpdate(element.copy(captionSizePx = it)) }
            }
        }
    }
}

/**
 * Size of the caption band, in the stepper a text element sizes its font with, with automatic
 * sitting one step below the smallest height that can be set. So the two ends meet: stepping down
 * off the smallest height reaches it, and stepping up off it lands back on that height.
 */
@Composable
private fun CaptionSizeSpinner(element: BarcodeElement, onUpdate: (Float?) -> Unit) {
    val range = LabelRenderer.MIN_CAPTION_PX..LabelRenderer.MAX_CAPTION_PX
    val size = element.captionSizePx?.roundToInt()
    Stepper(
        label = "",
        value = size?.let { "$it px" } ?: stringResource(R.string.size_auto),
        readings = listOf("${LabelRenderer.MAX_CAPTION_PX} px", stringResource(R.string.size_auto)),
        onDecrease = {
            // Already at the bottom when it is automatic, so nothing below it to go to.
            val next = (size ?: return@Stepper) - CAPTION_SIZE_STEP
            onUpdate(if (next < range.first) null else next.toFloat())
        },
        onIncrease = { onUpdate((size?.plus(CAPTION_SIZE_STEP) ?: range.first).coerceIn(range).toFloat()) },
        edit = NumberEdit(
            title = stringResource(R.string.prop_size),
            value = size ?: LabelRenderer.autoCaptionHeightPx(element.heightPx).roundToInt(),
            range = range,
            onValue = { onUpdate(it.toFloat()) },
        ),
    )
}

/** Dots a tap on the caption spinner is worth. Its whole range is thirty-odd dots wide. */
private const val CAPTION_SIZE_STEP = 2

/**
 * Font for the caption under a bar code, in the order a text element lists them: the built-in
 * fonts, then the imported ones.
 *
 * A dropdown and not the chip row a text element gets, because that row runs over four lines and
 * this one sits next to a switch.
 */
@Composable
private fun CaptionFontPicker(
    font: LabelFont,
    custom: String?,
    modifier: Modifier = Modifier,
    onSelect: (LabelFont, String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val customFonts by rememberFontRepository().fonts.collectAsState()
    val chosen = customFonts.firstOrNull { it.family == custom }
    Box(modifier) {
        Surface(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                    // An uninstalled font is shown by its bare family name, which is the only
                    // thing left of it, rather than silently reading as the fallback.
                    if (custom != null) CustomFontLabel(chosen?.label ?: custom, custom)
                    else FontLabel(font)
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.group_font),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LabelFont.entries.forEach { f ->
                DropdownMenuItem(
                    text = { FontLabel(f) },
                    trailingIcon = {
                        if (custom == null && font == f) Icon(Icons.Default.Check, contentDescription = null)
                    },
                    onClick = { open = false; onSelect(f, null) },
                )
            }
            customFonts.forEach { c ->
                DropdownMenuItem(
                    text = { CustomFontLabel(c.label, c.family) },
                    trailingIcon = {
                        if (custom == c.family) Icon(Icons.Default.Check, contentDescription = null)
                    },
                    // font is left alone on purpose, it stays the fallback for this element.
                    onClick = { open = false; onSelect(font, c.family) },
                )
            }
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

/**
 * Picks the code type. A menu rather than a row of chips, because seven types no longer fit on one
 * line. In here each one has room for its name, a word on what it is good for, and a glyph of the
 * shape it comes out as, which is exactly what the choice between QR and rMQR is about.
 */
@Composable
private fun SymbologyPicker(current: Symbology, onSelect: (Symbology) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val fieldPadding = 10.dp
    val arrowSize = 24.dp
    BoxWithConstraints {
        // The menu stops where the arrow begins, so it sits under the name rather than under the
        // whole field. Setting the width also lifts Material's 280 dp cap on a menu item, which is
        // narrow enough to break the longer descriptions onto a second line.
        val itemWidth = maxWidth - fieldPadding - arrowSize
        Surface(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = fieldPadding, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(symbologyIcon(current)), contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(symbologyName(current), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(arrowSize))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Symbology.entries.forEach { s ->
                // A rule sets the two matrix codes off from the bar codes.
                if (s == Symbology.CODE_128) HorizontalDivider()
                DropdownMenuItem(
                    modifier = Modifier.width(itemWidth),
                    text = {
                        Column {
                            Text(symbologyName(s))
                            Text(
                                stringResource(symbologyHint(s)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(painterResource(symbologyIcon(s)), contentDescription = null)
                    },
                    trailingIcon = {
                        if (s == current) Icon(Icons.Default.Check, contentDescription = null)
                    },
                    onClick = { open = false; onSelect(s) },
                )
            }
        }
    }
}

/** The glyph shows the family: the two matrix codes get their own, the bar codes share one. */
private fun symbologyIcon(s: Symbology): Int = when (s) {
    Symbology.QR_CODE -> R.drawable.ic_code_qr
    Symbology.RMQR -> R.drawable.ic_code_rmqr
    Symbology.DATA_MATRIX -> R.drawable.ic_code_datamatrix
    else -> R.drawable.ic_code_bars
}

private fun symbologyHint(s: Symbology): Int = when (s) {
    Symbology.QR_CODE -> R.string.code_hint_qr
    Symbology.RMQR -> R.string.code_hint_rmqr
    Symbology.DATA_MATRIX -> R.string.code_hint_datamatrix
    Symbology.CODE_128 -> R.string.code_hint_code128
    Symbology.EAN_13 -> R.string.code_hint_ean13
    Symbology.UPC_A -> R.string.code_hint_upca
    Symbology.CODE_39 -> R.string.code_hint_code39
    Symbology.ITF -> R.string.code_hint_itf
}

/** The full name, for the picker. [symbologyLabel] stays short for the crowded element chip. */
private fun symbologyName(s: Symbology): String = when (s) {
    Symbology.QR_CODE -> "QR Code"
    Symbology.RMQR -> "rMQR Code"
    Symbology.DATA_MATRIX -> "Data Matrix"
    else -> symbologyLabel(s)
}

private fun symbologyLabel(s: Symbology): String = when (s) {
    Symbology.QR_CODE -> "QR"
    Symbology.RMQR -> "rMQR"
    Symbology.DATA_MATRIX -> "DM"
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
                edit = NumberEdit(
                    title = stringResource(R.string.prop_line_width),
                    value = element.outlineThickness,
                    range = 1..3,
                    onValue = { onUpdate(element.copy(outlineThickness = it)) },
                ),
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
private fun TextProperties(
    element: TextElement,
    onUpdate: (LabelElement) -> Unit,
    onOpenFonts: () -> Unit,
) {
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
        onIncrease = {
            onUpdate(element.copy(fontSizePx = (element.fontSizePx + 4).coerceAtMost(MAX_FONT_SIZE_PX.toFloat())))
        },
        edit = NumberEdit(
            title = stringResource(R.string.prop_size),
            value = element.fontSizePx.toInt(),
            range = 8..MAX_FONT_SIZE_PX,
            onValue = { onUpdate(element.copy(fontSizePx = it.toFloat())) },
        ),
    )

    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Aligned by baseline, not by box, so the button's padding and border sit around its
        // caption instead of shifting it against the heading.
        GroupLabel(stringResource(R.string.group_font), Modifier.alignByBaseline())
        SectionActionButton(
            stringResource(R.string.fonts_title),
            onOpenFonts,
            Modifier.alignByBaseline(),
        )
    }
    val fontRepository = rememberFontRepository()
    val customFonts by fontRepository.fonts.collectAsState()
    val fontsReady by fontRepository.ready.collectAsState()
    // The reference survives an uninstalled font, so it has to be shown as such instead of
    // silently looking like the fallback the canvas draws. Reporting it waits for the initial
    // load, otherwise every custom font would raise a false alarm right after a cold start.
    val missingFont = element.customFont
        ?.takeIf { family -> fontsReady && customFonts.none { it.family == family } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LabelFont.entries.forEach { f ->
            ChoiceChip(
                selected = element.customFont == null && element.font == f,
                onClick = { onUpdate(element.copy(font = f, customFont = null)) },
                label = { FontLabel(f) },
            )
        }
        customFonts.forEach { custom ->
            ChoiceChip(
                selected = element.customFont == custom.family,
                // element.font is left alone on purpose, it stays the fallback for this element.
                onClick = { onUpdate(element.copy(customFont = custom.family)) },
                label = { CustomFontLabel(custom.label, custom.family) },
            )
        }
        if (missingFont != null) {
            ChoiceChip(
                selected = true,
                error = true,
                onClick = onOpenFonts,
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        FontChipLabel(missingFont, null)
                    }
                }
            )
        }
    }
    if (missingFont != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.fonts_missing_hint, missingFont),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
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
    val append: (String) -> Unit = { token -> onUpdate(element.copy(text = element.text + token)) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StampChip(stringResource(R.string.var_date), "date", DATE_PATTERNS, append)
        StampChip(stringResource(R.string.var_time), "time", TIME_PATTERNS, append)
        ChoiceChip(
            selected = false,
            onClick = { append("{#}") },
            label = { Text(stringResource(R.string.var_number)) },
        )
        ChoiceChip(
            selected = false,
            onClick = { append("{var:Text}") },
            label = { Text(stringResource(R.string.var_var)) },
        )
    }
}

// An empty entry stands for the plain token, which prints in the format of the device.
private val DATE_PATTERNS = listOf("", "dd.MM.yyyy", "yyyy-MM-dd", "MM/dd/yyyy", "d MMM yyyy", "EEE dd.MM.")
private val TIME_PATTERNS = listOf("", "HH:mm", "HH:mm:ss", "h:mm a")

/**
 * Inserts {date} or {time}. A tap inserts the plain token, which is what nearly everyone wants and
 * what the chip did before the formats existed; a long press opens them. Each entry shows today in
 * its format, so nobody has to know what yyyy or EEE stand for, and spells out the token it
 * inserts, which is what someone needs in order to type a format of their own later.
 */
@Composable
private fun StampChip(label: String, token: String, patterns: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val now = remember { Date() }
    val hint = stringResource(R.string.var_format_hint)
    Box {
        ChoiceChip(
            selected = false,
            // Nothing about the chip reveals that the formats are there, so the tap says it.
            onClick = {
                onPick("{$token}")
                Toast.makeText(context, hint, Toast.LENGTH_SHORT).show()
            },
            onLongClick = { open = true },
            label = { Text(label) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            patterns.forEach { pattern ->
                val inserted = if (pattern.isEmpty()) "{$token}" else "{$token:$pattern}"
                val example = remember(pattern, context) {
                    when {
                        pattern.isNotEmpty() ->
                            runCatching { SimpleDateFormat(pattern, systemLocale(context)).format(now) }
                                .getOrDefault(pattern)
                        token == "date" -> appDateFormat(context).format(now)
                        else -> appTimeFormat(context).format(now)
                    }
                }
                DropdownMenuItem(
                    text = { Text(example) },
                    trailingIcon = {
                        Text(
                            inserted,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { open = false; onPick(inserted) },
                )
            }
        }
    }
}

/**
 * A symbol the way the label draws it, centered on its own outline. Compose centers the text line
 * instead, and a font that reserves room above for accents and below for tails then leaves the
 * symbol sitting low in the box.
 */
@Composable
private fun GlyphPreview(glyph: String, iconFont: String?, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    val paint = remember(iconFont, color) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            FontRegistry.iconFont(iconFont)?.let { typeface = it }
        }
    }
    Canvas(modifier) {
        // The square it gets is the em square of the glyph, the same rule the label draws by, so a
        // symbol is as large here as it is there. One that reaches past its square is scaled down
        // until it fits, again as on the label.
        val box = minOf(size.width, size.height)
        val ink = android.graphics.Rect()
        paint.textSize = box
        paint.getTextBounds(glyph, 0, glyph.length, ink)
        val over = maxOf(ink.width(), ink.height()) / box
        if (over > 1f) {
            paint.textSize = box / over
            paint.getTextBounds(glyph, 0, glyph.length, ink)
        }
        drawIntoCanvas {
            it.nativeCanvas.drawText(
                glyph,
                size.width / 2f - (ink.left + ink.right) / 2f,
                size.height / 2f - (ink.top + ink.bottom) / 2f,
                paint,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconProperties(
    element: IconElement,
    geometry: HeadGeometry,
    onUpdate: (LabelElement) -> Unit,
) {
    val maxHeightPx = maxElementHeightPx(geometry)
    var showPicker by remember { mutableStateOf(false) }
    GroupLabel(stringResource(R.string.symbol_current))
    Box(
        modifier = Modifier
            .size(72.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable { showPicker = true },
        contentAlignment = Alignment.Center
    ) {
        GlyphPreview(element.glyph, element.iconFont, Modifier.fillMaxSize().padding(4.dp))
    }
    Spacer(Modifier.height(8.dp))
    Stepper(
        label = stringResource(R.string.prop_size) + ": ",
        value = "${element.sizePx.toInt()} px",
        onDecrease = { onUpdate(element.copy(sizePx = (element.sizePx - 8).coerceAtLeast(16f))) },
        onIncrease = {
            onUpdate(element.copy(sizePx = (element.sizePx + 8).coerceAtMost(maxHeightPx.toFloat())))
        },
        edit = NumberEdit(
            title = stringResource(R.string.prop_size),
            value = element.sizePx.toInt(),
            range = 16..maxHeightPx,
            onValue = { onUpdate(element.copy(sizePx = it.toFloat())) },
        ),
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
            onIncrease = { onUpdate(element.copy(outlineThickness = (element.outlineThickness + 1).coerceAtMost(3))) },
            edit = NumberEdit(
                title = stringResource(R.string.prop_line_width),
                value = element.outlineThickness,
                range = 1..3,
                onValue = { onUpdate(element.copy(outlineThickness = it)) },
            ),
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
            onPick = { glyph, iconFont, isEmoji ->
                // An icon out of a font is a flat black shape, and outlining one leaves nothing but
                // a hollow contour, so it always lands on threshold. For the rest the default is set
                // only on first assignment (still the placeholder glyph), emoji to outline and the
                // single-color symbols to threshold; on a later change the choice stays.
                val newDither = when {
                    iconFont != null -> DitherMode.THRESHOLD
                    element.glyph != "□" -> element.dither
                    isEmoji -> DitherMode.OUTLINE
                    else -> DitherMode.THRESHOLD
                }
                onUpdate(element.copy(glyph = glyph, iconFont = iconFont, dither = newDither))
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun FrameProperties(
    element: FrameElement,
    geometry: HeadGeometry,
    onUpdate: (LabelElement) -> Unit,
) {
    val maxHeightPx = maxElementHeightPx(geometry)
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
        onIncrease = { onUpdate(element.copy(strokePx = (element.strokePx + 1).coerceAtMost(10f))) },
        edit = NumberEdit(
            title = stringResource(R.string.prop_stroke),
            value = element.strokePx.toInt(),
            range = 1..10,
            onValue = { onUpdate(element.copy(strokePx = it.toFloat())) },
        ),
    )
    if (rectSelected) {
        Stepper(
            label = stringResource(R.string.prop_radius) + ": ",
            value = "${element.cornerRadiusPx.toInt()} px",
            onDecrease = { onUpdate(element.copy(cornerRadiusPx = (element.cornerRadiusPx - 2).coerceAtLeast(0f))) },
            onIncrease = { onUpdate(element.copy(cornerRadiusPx = (element.cornerRadiusPx + 2).coerceAtMost(48f))) },
            edit = NumberEdit(
                title = stringResource(R.string.prop_radius),
                value = element.cornerRadiusPx.toInt(),
                range = 0..48,
                onValue = { onUpdate(element.copy(cornerRadiusPx = it.toFloat())) },
            ),
        )
    }
    Stepper(
        label = stringResource(R.string.prop_width) + ": ",
        value = "${element.widthPx.toInt()} px",
        onDecrease = { onUpdate(element.copy(widthPx = (element.widthPx - 8).coerceAtLeast(8f))) },
        onIncrease = { onUpdate(element.copy(widthPx = element.widthPx + 8)) },
        edit = NumberEdit(
            title = stringResource(R.string.prop_width),
            value = element.widthPx.toInt(),
            // A frame may run the whole length of the label, so its width stops where the label does.
            range = 8..geometry.maxLengthDots,
            onValue = { onUpdate(element.copy(widthPx = it.toFloat())) },
        ),
    )
    Stepper(
        label = stringResource(R.string.prop_height) + ": ",
        value = "${element.heightPx.toInt()} px",
        onDecrease = { onUpdate(element.copy(heightPx = (element.heightPx - 8).coerceAtLeast(8f))) },
        onIncrease = {
            onUpdate(element.copy(heightPx = (element.heightPx + 8).coerceAtMost(maxHeightPx.toFloat())))
        },
        edit = NumberEdit(
            title = stringResource(R.string.prop_height),
            value = element.heightPx.toInt(),
            range = 8..maxHeightPx,
            onValue = { onUpdate(element.copy(heightPx = it.toFloat())) },
        ),
    )
}
