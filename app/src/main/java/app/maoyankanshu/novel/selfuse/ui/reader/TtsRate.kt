package app.maoyankanshu.novel.selfuse.ui.reader

import kotlin.math.abs

/**
 * Pure speech-rate helpers for in-reader TTS.
 * Matches [app.maoyankanshu.novel.selfuse.ReaderPreferences] clamp range 0.5…2.0.
 */
object TtsRate {
    const val MIN: Float = 0.5f
    const val MAX: Float = 2.0f
    const val DEFAULT: Float = 1.0f

    /** Common CN-reader presets (slow → fast). */
    val PRESETS: FloatArray = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f)

    fun clamp(rate: Float): Float {
        if (!rate.isFinite()) return DEFAULT
        return rate.coerceIn(MIN, MAX)
    }

    /** Nearest preset within [tolerance], else [clamp]ed raw rate. */
    fun nearestPreset(rate: Float, tolerance: Float = 0.05f): Float {
        val c = clamp(rate)
        var best = c
        var bestDist = Float.MAX_VALUE
        for (p in PRESETS) {
            val d = abs(p - c)
            if (d < bestDist) {
                bestDist = d
                best = p
            }
        }
        return if (bestDist <= tolerance) best else c
    }

    fun isPresetSelected(rate: Float, preset: Float, tolerance: Float = 0.05f): Boolean =
        abs(clamp(rate) - clamp(preset)) < tolerance

    /** Display label e.g. `1.25×`. */
    fun label(rate: Float): String {
        val c = clamp(rate)
        val rounded = (c * 100f).toInt() / 100f
        return if (rounded == rounded.toInt().toFloat()) {
            "${rounded.toInt()}×"
        } else {
            // Trim trailing zeros in a locale-stable way.
            val s = String.format(java.util.Locale.US, "%.2f", c).trimEnd('0').trimEnd('.')
            "${s}×"
        }
    }
}
