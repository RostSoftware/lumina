package rocks.lumina.app.feature.create

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Size
import kotlin.math.roundToInt
import rocks.lumina.app.core.designsystem.component.ButtonStyle
import rocks.lumina.app.core.designsystem.component.ContentColumn
import rocks.lumina.app.core.designsystem.component.LuminaButton
import rocks.lumina.app.core.designsystem.component.LuminaChip
import rocks.lumina.app.core.designsystem.component.LuminaTopBar
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaTheme

/**
 * Renders [model] cropped to [crop] (frame aspect, zoom, center). The image is laid out at the size where the crop
 * frame exactly matches this composable's bounds, then offset so the frame shows the selected region.
 */
@Composable
fun CroppedImage(
    model: Any?,
    imageAspect: Float,
    crop: CropSpec,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    maxDecodeSize: Int = 2048,
) {
    val frameAspect = CropMath.frameAspect(crop, imageAspect)
    val clamped = CropMath.clamp(crop, imageAspect)
    val (fw, fh) = CropMath.fractions(imageAspect, frameAspect, clamped.zoom)
    val context = LocalContext.current
    val request = remember(model, maxDecodeSize) {
        ImageRequest.Builder(context).data(model).size(Size(maxDecodeSize, maxDecodeSize)).precision(Precision.INEXACT).build()
    }
    Box(
        modifier
            .aspectRatio(frameAspect)
            .clipToBounds()
            .background(LuminaTheme.colors.surfaceMuted),
    ) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val w = constraints.maxWidth
                    val h = constraints.maxHeight
                    if (w == Constraints.Infinity || h == Constraints.Infinity) {
                        val placeable = measurable.measure(constraints)
                        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    val dispW = (w / fw).roundToInt().coerceAtLeast(1)
                    val dispH = (h / fh).roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(dispW, dispH))
                    layout(w, h) {
                        placeable.place(
                            -((clamped.centerX - fw / 2f) * dispW).roundToInt(),
                            -((clamped.centerY - fh / 2f) * dispH).roundToInt(),
                        )
                    }
                },
        )
    }
}

/** Full-screen crop editor: aspect presets plus pinch-zoom / drag inside the crop frame. */
@Composable
fun CropEditor(
    image: DraftImage,
    onCancel: () -> Unit,
    onDone: (CropSpec) -> Unit,
) {
    var spec by remember(image.id) { mutableStateOf(image.crop) }
    BackHandler(onBack = onCancel)
    CropEditorContent(
        model = image.uri,
        imageAspect = image.imageAspect,
        spec = spec,
        onSpecChange = { spec = it },
        onCancel = onCancel,
        onDone = { onDone(spec) },
    )
}

@Composable
private fun CropEditorContent(
    model: Any?,
    imageAspect: Float,
    spec: CropSpec,
    onSpecChange: (CropSpec) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = LuminaTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .navigationBarsPadding(),
    ) {
        LuminaTopBar(
            leading = {
                LuminaButton(
                    text = stringResource(R.string.create_crop_cancel),
                    onClick = onCancel,
                    style = ButtonStyle.Ghost,
                )
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.create_crop_title), style = LuminaTheme.typography.title, color = colors.foreground)
            },
            actions = {
                LuminaButton(text = stringResource(R.string.create_crop_done), onClick = onDone, style = ButtonStyle.Ghost)
            },
        )
        ContentColumn(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(LuminaDimens.gutter),
                    contentAlignment = Alignment.Center,
                ) {
                    val frameAspect = CropMath.frameAspect(spec, imageAspect)
                    val hint = stringResource(R.string.create_crop_hint)
                    CropFrame(
                        model = model,
                        imageAspect = imageAspect,
                        spec = spec,
                        onSpecChange = onSpecChange,
                        modifier = Modifier
                            .aspectRatio(frameAspect, matchHeightConstraintsFirst = frameAspect < 1f)
                            .semantics { contentDescription = hint },
                    )
                }
                Text(
                    stringResource(R.string.create_crop_hint),
                    style = LuminaTheme.typography.caption,
                    color = colors.mutedForeground,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = LuminaDimens.gutter, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AspectPreset.entries.forEach { preset ->
                        LuminaChip(
                            text = stringResource(preset.label()),
                            selected = spec.aspect == preset,
                            onClick = { onSpecChange(CropMath.clamp(spec.copy(aspect = preset), imageAspect)) },
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    LuminaButton(
                        text = stringResource(R.string.create_crop_reset),
                        onClick = { onSpecChange(CropSpec(aspect = spec.aspect)) },
                        style = ButtonStyle.Ghost,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CropFrame(
    model: Any?,
    imageAspect: Float,
    spec: CropSpec,
    onSpecChange: (CropSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    var frameWidth by remember { mutableFloatStateOf(0f) }
    val currentSpec by rememberUpdatedState(spec)
    val currentOnChange by rememberUpdatedState(onSpecChange)
    Box(
        modifier
            .onSizeChanged { frameWidth = it.width.toFloat() }
            .pointerInput(imageAspect) {
                detectTransformGestures { _, pan: Offset, zoom, _ ->
                    currentOnChange(CropMath.applyGesture(currentSpec, imageAspect, frameWidth, pan.x, pan.y, zoom))
                }
            },
    ) {
        CroppedImage(model = model, imageAspect = imageAspect, crop = spec, contentDescription = null, modifier = Modifier.fillMaxSize())
        // Rule-of-thirds grid on top of the photo.
        Canvas(Modifier.fillMaxSize()) {
            val line = Color.White.copy(alpha = 0.6f)
            val stroke = 1.dp.toPx()
            for (i in 1..2) {
                val x = size.width * i / 3f
                val y = size.height * i / 3f
                drawLine(line, Offset(x, 0f), Offset(x, size.height), stroke)
                drawLine(line, Offset(0f, y), Offset(size.width, y), stroke)
            }
            drawRect(Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke * 1.5f))
        }
    }
}

fun AspectPreset.label(): Int = when (this) {
    AspectPreset.Original -> R.string.create_aspect_original
    AspectPreset.Square -> R.string.create_aspect_square
    AspectPreset.Portrait -> R.string.create_aspect_portrait
    AspectPreset.Landscape -> R.string.create_aspect_landscape
}

@Preview(name = "Crop light")
@Composable
private fun CropEditorLightPreview() {
    LuminaTheme(dark = false) {
        CropEditorContent(null, 1.5f, CropSpec(AspectPreset.Square, 1.4f), {}, {}, {})
    }
}

@Preview(name = "Crop dark")
@Composable
private fun CropEditorDarkPreview() {
    LuminaTheme(dark = true) {
        CropEditorContent(null, 0.75f, CropSpec(AspectPreset.Landscape), {}, {}, {})
    }
}
