package rocks.lumina.app.feature.create

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rocks.lumina.app.core.media.CropRect

class CropMathTest {
    private val eps = 1e-4f

    private fun assertRect(expected: CropRect, actual: CropRect) {
        assertEquals(expected.left, actual.left, eps)
        assertEquals(expected.top, actual.top, eps)
        assertEquals(expected.right, actual.right, eps)
        assertEquals(expected.bottom, actual.bottom, eps)
    }

    @Test
    fun `original at zoom 1 is full`() {
        assertEquals(CropRect.Full, CropMath.toCropRect(CropSpec(), 4000, 3000))
    }

    @Test
    fun `square crop of landscape is centered`() {
        val rect = CropMath.toCropRect(CropSpec(AspectPreset.Square), 4000, 2000)
        assertRect(CropRect(0.25f, 0f, 0.75f, 1f), rect)
    }

    @Test
    fun `square crop of portrait is centered vertically`() {
        val rect = CropMath.toCropRect(CropSpec(AspectPreset.Square), 1000, 2000)
        assertRect(CropRect(0f, 0.25f, 1f, 0.75f), rect)
    }

    @Test
    fun `landscape crop produces 16 by 9 pixels`() {
        val w = 3000
        val h = 3000
        val rect = CropMath.toCropRect(CropSpec(AspectPreset.Landscape), w, h)
        val ratio = (rect.right - rect.left) * w / ((rect.bottom - rect.top) * h)
        assertEquals(16f / 9f, ratio, 1e-3f)
    }

    @Test
    fun `zoom shrinks the frame and center is clamped inside the image`() {
        val rect = CropMath.toCropRect(CropSpec(AspectPreset.Original, zoom = 2f, centerX = 0f, centerY = 1f), 1000, 1000)
        assertRect(CropRect(0f, 0.5f, 0.5f, 1f), rect)
    }

    @Test
    fun `zoom is limited`() {
        val spec = CropMath.clamp(CropSpec(zoom = 50f), 1f)
        assertEquals(CropMath.MAX_ZOOM, spec.zoom, eps)
        assertEquals(1f, CropMath.clamp(CropSpec(zoom = 0.1f), 1f).zoom, eps)
    }

    @Test
    fun `dragging right moves the frame left`() {
        val start = CropSpec(AspectPreset.Square, zoom = 2f)
        val moved = CropMath.applyGesture(start, imageAspect = 1f, frameWidthPx = 1000f, panX = 100f, panY = 0f, zoomChange = 1f)
        // Frame covers half the image: 100px of a 1000px frame = 0.05 of the image.
        assertEquals(0.45f, moved.centerX, eps)
        assertEquals(0.5f, moved.centerY, eps)
    }

    @Test
    fun `pan without zoom on original keeps full`() {
        val moved = CropMath.applyGesture(CropSpec(), 1.5f, 1000f, 300f, 300f, 1f)
        assertTrue(moved.isIdentity)
        assertEquals(CropRect.Full, CropMath.toCropRect(moved, 1500, 1000))
    }
}
