package rocks.lumina.app.feature.create

import rocks.lumina.app.core.media.CropRect

/** Crop frame aspect presets. [ratio] is width / height; null = the image's own aspect. */
enum class AspectPreset(val ratio: Float?) {
    Original(null),
    Square(1f),
    Portrait(4f / 5f),
    Landscape(16f / 9f),
}

/**
 * Crop state of one image: the frame aspect, zoom (1 = the image just covers the frame) and the frame center in
 * normalised image coordinates (0..1 of the EXIF-oriented image).
 */
data class CropSpec(
    val aspect: AspectPreset = AspectPreset.Original,
    val zoom: Float = 1f,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
) {
    val isIdentity: Boolean get() = aspect == AspectPreset.Original && zoom <= 1f
}

/** Pure crop math shared by the crop editor, the previews and the uploader. */
object CropMath {
    const val MAX_ZOOM = 5f

    /** Frame aspect for [spec] on an image of [imageAspect] (w / h). */
    fun frameAspect(spec: CropSpec, imageAspect: Float): Float = spec.aspect.ratio ?: imageAspect

    /**
     * Size of the crop frame as a fraction of the image (width, height), with the image covering the frame at
     * zoom 1.
     */
    fun fractions(imageAspect: Float, frameAspect: Float, zoom: Float): Pair<Float, Float> {
        val z = zoom.coerceIn(1f, MAX_ZOOM)
        return if (imageAspect > frameAspect) {
            // Image wider than the frame: full height, partial width.
            (frameAspect / imageAspect) / z to 1f / z
        } else {
            1f / z to (imageAspect / frameAspect) / z
        }
    }

    /** Clamps zoom and center so the frame always stays inside the image. */
    fun clamp(spec: CropSpec, imageAspect: Float): CropSpec {
        val zoom = spec.zoom.coerceIn(1f, MAX_ZOOM)
        val (fw, fh) = fractions(imageAspect, frameAspect(spec, imageAspect), zoom)
        return spec.copy(
            zoom = zoom,
            centerX = spec.centerX.coerceIn(fw / 2f, 1f - fw / 2f),
            centerY = spec.centerY.coerceIn(fh / 2f, 1f - fh / 2f),
        )
    }

    /** The normalised crop rectangle handed to `ImageProcessor.prepare`. Identity crops return [CropRect.Full]. */
    fun toCropRect(spec: CropSpec, imageWidth: Int, imageHeight: Int): CropRect {
        if (imageWidth <= 0 || imageHeight <= 0) return CropRect.Full
        val imageAspect = imageWidth.toFloat() / imageHeight
        val clamped = clamp(spec, imageAspect)
        if (clamped.isIdentity) return CropRect.Full
        val (fw, fh) = fractions(imageAspect, frameAspect(clamped, imageAspect), clamped.zoom)
        val rect = CropRect(
            left = (clamped.centerX - fw / 2f).coerceIn(0f, 1f),
            top = (clamped.centerY - fh / 2f).coerceIn(0f, 1f),
            right = (clamped.centerX + fw / 2f).coerceIn(0f, 1f),
            bottom = (clamped.centerY + fh / 2f).coerceIn(0f, 1f),
        )
        return if (rect.left <= EPS && rect.top <= EPS && rect.right >= 1f - EPS && rect.bottom >= 1f - EPS) CropRect.Full else rect
    }

    /**
     * Applies a gesture: [panX]/[panY] in frame pixels (finger movement), [zoomChange] multiplicative, on a frame
     * that is [frameWidthPx] wide.
     */
    fun applyGesture(spec: CropSpec, imageAspect: Float, frameWidthPx: Float, panX: Float, panY: Float, zoomChange: Float): CropSpec {
        if (frameWidthPx <= 0f) return spec
        val zoomed = clamp(spec.copy(zoom = spec.zoom * zoomChange), imageAspect)
        val frameAspect = frameAspect(zoomed, imageAspect)
        val (fw, fh) = fractions(imageAspect, frameAspect, zoomed.zoom)
        val frameHeightPx = frameWidthPx / frameAspect
        // Moving the finger right moves the image right, i.e. the frame center left.
        val moved = zoomed.copy(
            centerX = zoomed.centerX - panX / frameWidthPx * fw,
            centerY = zoomed.centerY - panY / frameHeightPx * fh,
        )
        return clamp(moved, imageAspect)
    }

    private const val EPS = 0.0005f
}
