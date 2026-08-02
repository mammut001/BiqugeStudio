package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.absoluteValue

/** JVM tests for shipped [PageTurnEffect] (left/right page-turn visual math). */
class PageTurnEffectTest {

    @Test
    fun settledPage_isIdentity() {
        val t = PageTurnEffect.transform(0f)
        assertEquals(0f, t.rotationY, 0.001f)
        assertEquals(1f, t.alpha, 0.001f)
        assertEquals(1f, t.scale, 0.001f)
        assertEquals(0.5f, t.pivotFractionX, 0.001f)
    }

    @Test
    fun swipeTowardNext_currentPagePivotsRightEdge() {
        // Positive offset → leaving left, pivot right, positive rotationY.
        val t = PageTurnEffect.transform(0.5f)
        assertTrue(t.rotationY > 0f)
        assertEquals(1f, t.pivotFractionX, 0.001f)
        assertTrue(t.alpha < 1f)
        assertTrue(t.scale < 1f)
    }

    @Test
    fun swipeTowardNext_incomingPagePivotsLeftEdge() {
        // Negative offset → entering from right, pivot left, negative rotationY.
        val t = PageTurnEffect.transform(-0.5f)
        assertTrue(t.rotationY < 0f)
        assertEquals(0f, t.pivotFractionX, 0.001f)
    }

    @Test
    fun fullOffset_hitsMaxRotationMagnitude() {
        val next = PageTurnEffect.transform(1f)
        val prev = PageTurnEffect.transform(-1f)
        assertEquals(PageTurnEffect.MAX_ROTATION_DEG, next.rotationY.absoluteValue, 0.001f)
        assertEquals(PageTurnEffect.MAX_ROTATION_DEG, prev.rotationY.absoluteValue, 0.001f)
        assertEquals(next.rotationY, -prev.rotationY, 0.001f)
    }

    @Test
    fun outOfRange_clampedToUnit() {
        val over = PageTurnEffect.transform(2f)
        val unit = PageTurnEffect.transform(1f)
        assertEquals(unit.rotationY, over.rotationY, 0.001f)
        assertEquals(unit.alpha, over.alpha, 0.001f)
    }
}
