package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for shipped [TtsRate] — drives real clamp/presets used by reader UI. */
class TtsRateTest {

    @Test
    fun clamp_boundsAndDefaultForNonFinite() {
        assertEquals(TtsRate.DEFAULT, TtsRate.clamp(Float.NaN), 0.001f)
        assertEquals(TtsRate.DEFAULT, TtsRate.clamp(Float.POSITIVE_INFINITY), 0.001f)
        assertEquals(TtsRate.MIN, TtsRate.clamp(0.1f), 0.001f)
        assertEquals(TtsRate.MAX, TtsRate.clamp(9f), 0.001f)
        assertEquals(1.25f, TtsRate.clamp(1.25f), 0.001f)
    }

    @Test
    fun nearestPreset_snapsNearValues() {
        assertEquals(1.0f, TtsRate.nearestPreset(1.02f), 0.001f)
        assertEquals(1.25f, TtsRate.nearestPreset(1.24f), 0.001f)
        assertEquals(0.75f, TtsRate.nearestPreset(0.76f), 0.001f)
    }

    @Test
    fun isPresetSelected_matchesTolerance() {
        assertTrue(TtsRate.isPresetSelected(1.0f, 1.0f))
        assertTrue(TtsRate.isPresetSelected(1.03f, 1.0f))
        assertFalse(TtsRate.isPresetSelected(1.25f, 1.0f))
    }

    @Test
    fun label_formatsReadable() {
        assertEquals("1×", TtsRate.label(1.0f))
        assertEquals("0.75×", TtsRate.label(0.75f))
        assertEquals("1.25×", TtsRate.label(1.25f))
        assertEquals("2×", TtsRate.label(2.0f))
    }

    @Test
    fun presets_areWithinClampRange() {
        for (p in TtsRate.PRESETS) {
            assertEquals(p, TtsRate.clamp(p), 0.001f)
        }
    }
}
