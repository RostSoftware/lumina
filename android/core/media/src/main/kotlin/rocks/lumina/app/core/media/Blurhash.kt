package rocks.lumina.app.core.media

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.withSign

/**
 * Blurhash encoder/decoder (https://blurha.sh, MIT). Quartz only models the tag (gap #2), so LUMINA
 * ships this ~150-line implementation. Encoding runs on a small downscaled bitmap.
 */
public object Blurhash {
    private const val CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"

    public fun encode(bitmap: Bitmap, componentsX: Int = 4, componentsY: Int = 3): String {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val factors = Array(componentsX * componentsY) { FloatArray(3) }
        for (j in 0 until componentsY) {
            for (i in 0 until componentsX) {
                val normalisation = if (i == 0 && j == 0) 1f else 2f
                var r = 0f; var g = 0f; var b = 0f
                for (y in 0 until height) {
                    val cy = cos(PI * j * y / height)
                    for (x in 0 until width) {
                        val basis = (normalisation * cos(PI * i * x / width) * cy).toFloat()
                        val p = pixels[y * width + x]
                        r += basis * srgbToLinear((p shr 16) and 0xff)
                        g += basis * srgbToLinear((p shr 8) and 0xff)
                        b += basis * srgbToLinear(p and 0xff)
                    }
                }
                val scale = 1f / (width * height)
                factors[j * componentsX + i] = floatArrayOf(r * scale, g * scale, b * scale)
            }
        }
        val sb = StringBuilder()
        sb.append(encode83((componentsX - 1) + (componentsY - 1) * 9, 1))
        val ac = factors.drop(1)
        val maximumValue: Float
        if (ac.isNotEmpty()) {
            val actualMax = ac.maxOf { f -> f.maxOf { abs(it) } }
            val quantisedMax = (actualMax * 166 - 0.5f).roundToInt().coerceIn(0, 82)
            maximumValue = (quantisedMax + 1) / 166f
            sb.append(encode83(quantisedMax, 1))
        } else {
            maximumValue = 1f
            sb.append(encode83(0, 1))
        }
        val dc = factors[0]
        sb.append(encode83((linearToSrgb(dc[0]) shl 16) + (linearToSrgb(dc[1]) shl 8) + linearToSrgb(dc[2]), 4))
        for (f in ac) {
            fun q(v: Float) = (signPow(v / maximumValue, 0.5f) * 9 + 9.5f).toInt().coerceIn(0, 18)
            sb.append(encode83(q(f[0]) * 19 * 19 + q(f[1]) * 19 + q(f[2]), 2))
        }
        return sb.toString()
    }

    /** Decodes [hash] into a small bitmap (e.g. 32x32) suitable for a stretched placeholder. */
    public fun decode(hash: String, width: Int = 32, height: Int = 32, punch: Float = 1f): Bitmap? {
        if (hash.length < 6) return null
        return runCatching {
            val sizeFlag = decode83(hash, 0, 1)
            val numY = sizeFlag / 9 + 1
            val numX = sizeFlag % 9 + 1
            if (hash.length != 4 + 2 * numX * numY) return null
            val quantisedMax = decode83(hash, 1, 2)
            val maxValue = (quantisedMax + 1) / 166f
            val colors = Array(numX * numY) { i ->
                if (i == 0) {
                    val value = decode83(hash, 2, 6)
                    floatArrayOf(srgbToLinear(value shr 16), srgbToLinear((value shr 8) and 255), srgbToLinear(value and 255))
                } else {
                    val from = 4 + i * 2
                    val value = decode83(hash, from, from + 2)
                    floatArrayOf(
                        signPow(((value / (19 * 19)) - 9) / 9f, 2f) * maxValue * punch,
                        signPow((((value / 19) % 19) - 9) / 9f, 2f) * maxValue * punch,
                        signPow(((value % 19) - 9) / 9f, 2f) * maxValue * punch,
                    )
                }
            }
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    var r = 0f; var g = 0f; var b = 0f
                    for (j in 0 until numY) {
                        val cy = cos(PI * y * j / height)
                        for (i in 0 until numX) {
                            val basis = (cos(PI * x * i / width) * cy).toFloat()
                            val c = colors[i + j * numX]
                            r += c[0] * basis; g += c[1] * basis; b += c[2] * basis
                        }
                    }
                    pixels[x + width * y] = (0xff shl 24) or (linearToSrgb(r) shl 16) or (linearToSrgb(g) shl 8) or linearToSrgb(b)
                }
            }
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    private fun encode83(value: Int, length: Int): String {
        val sb = StringBuilder()
        for (i in 1..length) {
            val digit = (value / 83.0.pow((length - i).toDouble())).toInt() % 83
            sb.append(CHARS[digit])
        }
        return sb.toString()
    }

    private fun decode83(str: String, from: Int, to: Int): Int {
        var result = 0
        for (i in from until to) {
            val index = CHARS.indexOf(str[i])
            if (index < 0) throw IllegalArgumentException("invalid blurhash")
            result = result * 83 + index
        }
        return result
    }

    private fun srgbToLinear(value: Int): Float {
        val v = value / 255f
        return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun linearToSrgb(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        return if (v <= 0.0031308f) (v * 12.92f * 255 + 0.5f).toInt() else ((1.055f * v.pow(1 / 2.4f) - 0.055f) * 255 + 0.5f).toInt()
    }

    private fun signPow(value: Float, exp: Float): Float = abs(value).pow(exp).withSign(sign(value)).let { if (it.isNaN()) 0f else it }

    @Suppress("unused")
    private fun clamp(v: Int) = max(0, v)
}
