package app.maoyankanshu.novel.selfuse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for pure [canAcceptUi]: finishing or destroyed hosts must not
 * receive Toast / finish / Compose state after [rememberCoroutineScope] work returns.
 * (Android [android.app.Activity] itself is not available on pure JVM unit tests.)
 */
class ActivityCanAcceptUiTest {

    @Test
    fun acceptsOnlyLiveActivity() {
        assertTrue(canAcceptUi(isFinishing = false, isDestroyed = false))
    }

    @Test
    fun rejectsFinishingOrDestroyed() {
        assertFalse(canAcceptUi(isFinishing = true, isDestroyed = false))
        assertFalse(canAcceptUi(isFinishing = false, isDestroyed = true))
        assertFalse(canAcceptUi(isFinishing = true, isDestroyed = true))
    }

    @Test
    fun finishingTakesPriorityOverDestroyedFalse() {
        // Explicit matrix edge: finishing alone is enough to reject UI side-effects.
        assertFalse(canAcceptUi(isFinishing = true, isDestroyed = false))
    }
}
