package app.maoyankanshu.novel.selfuse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM stand-in for [Activity.canAcceptUi]: finishing or destroyed hosts must not
 * receive Toast / finish / Compose state after [rememberCoroutineScope] work returns.
 * (Android [Activity] itself is not available on pure JVM unit tests.)
 */
class ActivityCanAcceptUiTest {

    private fun canAcceptUi(isFinishing: Boolean, isDestroyed: Boolean): Boolean {
        if (isFinishing) return false
        return !isDestroyed
    }

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
}
