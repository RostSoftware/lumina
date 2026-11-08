package rocks.lumina.app.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Normalised crop rectangle (0..1 of the oriented image). */
public data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    public companion object {
        public val Full: CropRect = CropRect(0f, 0f, 1f, 1f)
    }
}

/** An image ready for upload. Metadata (EXIF/GPS/XMP) is gone because pixels are re-encoded. */
public class PreparedImage(
    public val bytes: ByteArray,
    public val mimeType: String,
    public val width: Int,
    public val height: Int,
    public val sha256: String,
    public val blurhash: String?,
) {
    public val size: Long get() = bytes.size.toLong()
}

/**
 * Prepares picked images for Blossom (Quartz gap #6): applies EXIF orientation, crops, downsizes to at most
 * [MAX_EDGE] px, and **re-encodes** — which strips all EXIF/GPS/XMP metadata. PNG/GIF/WebP sources become
 * WebP, everything else JPEG (same policy as LUMINA web). Computes `dim`, `x` (sha256), `m`, `blurhash`.
 */
@Singleton
public class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    public suspend fun prepare(uri: Uri, crop: CropRect = CropRect.Full): PreparedImage = withContext(Dispatchers.Default) {
        val resolver = context.contentResolver
        val sourceMime = resolver.getType(uri)?.lowercase()
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalArgumentException("unsupported image")
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_EDGE && bounds.outHeight / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("cannot decode image")

        val oriented = applyOrientation(decoded, orientation)
        val cropped = applyCrop(oriented, crop)
        val scaled = scaleDown(cropped, MAX_EDGE)

        val webp = sourceMime == "image/png" || sourceMime == "image/gif" || sourceMime == "image/webp"
        val out = ByteArrayOutputStream()
        val mime = if (webp) {
            @Suppress("DEPRECATION")
            val format = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            scaled.compress(format, QUALITY, out)
            "image/webp"
        } else {
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            "image/jpeg"
        }
        val bytes = out.toByteArray()
        val blurhash = runCatching {
            val small = Bitmap.createScaledBitmap(scaled, 32, (32f * scaled.height / scaled.width).toInt().coerceIn(8, 64), true)
            Blurhash.encode(small)
        }.getOrNull()
        PreparedImage(bytes, mime, scaled.width, scaled.height, sha256Hex(bytes), blurhash)
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.preScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.preScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun applyCrop(bitmap: Bitmap, crop: CropRect): Bitmap {
        if (crop == CropRect.Full) return bitmap
        val rect = Rect(
            (crop.left.coerceIn(0f, 1f) * bitmap.width).toInt(),
            (crop.top.coerceIn(0f, 1f) * bitmap.height).toInt(),
            (crop.right.coerceIn(0f, 1f) * bitmap.width).toInt(),
            (crop.bottom.coerceIn(0f, 1f) * bitmap.height).toInt(),
        )
        if (rect.width() < 8 || rect.height() < 8) return bitmap
        return Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
    }

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }

    /** Pixel size of an image after EXIF orientation (for the crop UI). */
    public suspend fun orientedSize(uri: Uri): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return@withContext null
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val swapped = orientation in setOf(ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE)
        if (swapped) bounds.outHeight to bounds.outWidth else bounds.outWidth to bounds.outHeight
    }

    public companion object {
        public const val MAX_EDGE: Int = 2048
        private const val QUALITY = 88

        public fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
