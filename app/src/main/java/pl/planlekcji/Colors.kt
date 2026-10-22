package pl.planlekcji

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pomocnicze funkcje do rozpoznawania koloru wypełnienia komórek (np. żółty = zajęcia łączone). */
internal object ColorUtil {

    fun rgbToHsl(c: Int): FloatArray {
        val r = ((c shr 16) and 255) / 255f
        val g = ((c shr 8) and 255) / 255f
        val b = (c and 255) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        var h = 0f
        var s = 0f
        val d = mx - mn
        if (d > 1e-6f) {
            s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
            h = when {
                mx == r -> (g - b) / d + (if (g < b) 6f else 0f)
                mx == g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            } * 60f
        }
        return floatArrayOf(h, s, l)
    }

    fun hslToRgb(h: Float, s: Float, l: Float): Int {
        fun pack(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        if (s <= 0f) {
            val v = (l * 255f).roundToInt().coerceIn(0, 255)
            return pack(v, v, v)
        }
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        val hk = h / 360f
        fun f(t0: Float): Float {
            var t = t0
            if (t < 0f) t += 1f
            if (t > 1f) t -= 1f
            return when {
                t < 1f / 6f -> p + (q - p) * 6f * t
                t < 0.5f -> q
                t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
                else -> p
            }
        }
        val r = (f(hk + 1f / 3f) * 255f).roundToInt().coerceIn(0, 255)
        val g = (f(hk) * 255f).roundToInt().coerceIn(0, 255)
        val b = (f(hk - 1f / 3f) * 255f).roundToInt().coerceIn(0, 255)
        return pack(r, g, b)
    }

    /** Odpowiednik "tint" z Excela (jaśniejszy / ciemniejszy odcień koloru motywu). */
    fun applyTint(argb: Int, tint: Float): Int {
        if (tint == 0f) return argb
        val hsl = rgbToHsl(argb)
        val l = if (tint < 0f) hsl[2] * (1f + tint) else hsl[2] * (1f - tint) + tint
        return hslToRgb(hsl[0], hsl[1], l.coerceIn(0f, 1f))
    }

    /** Czy kolor jest odcieniem żółtego (także jasnożółtym lub złotym)? */
    fun isYellow(argb: Int): Boolean {
        val hsl = rgbToHsl(argb)
        return hsl[0] in 40f..70f && hsl[1] >= 0.35f && hsl[2] in 0.35f..0.97f
    }

    /** Stara paleta "indexed" – tylko żółte odcienie, które nas interesują. */
    val INDEXED_YELLOWS = mapOf(
        5 to 0xFFFFFF00.toInt(), 13 to 0xFFFFFF00.toInt(), 19 to 0xFFFFFFCC.toInt(),
        27 to 0xFFFFFF00.toInt(), 34 to 0xFFFFFF99.toInt(), 43 to 0xFFFFFF99.toInt(),
        51 to 0xFFFFCC00.toInt(),
    )
}
