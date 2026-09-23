package app.maoyankanshu.novel.selfuse.ui.reader

import app.maoyankanshu.novel.selfuse.ReaderPreferences
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
        assertEquals(0f, t.translationXFraction, 0.001f)
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

    @Test
    fun slideStyle_isFlatWithoutRotation() {
        val forward = PageTurnEffect.transform(0.5f, ReaderPreferences.PAGE_TURN_STYLE_SLIDE)
        assertEquals(0f, forward.rotationY, 0.001f)
        assertEquals(1f, forward.alpha, 0.001f)
        assertEquals(1f, forward.scale, 0.001f)
        assertEquals(0.5f, forward.pivotFractionX, 0.001f)
        assertEquals(0f, forward.translationXFraction, 0.001f)

        val backward = PageTurnEffect.transform(-0.5f, ReaderPreferences.PAGE_TURN_STYLE_SLIDE)
        assertEquals(0f, backward.rotationY, 0.001f)
        assertEquals(1f, backward.alpha, 0.001f)
        assertEquals(1f, backward.scale, 0.001f)
    }

    @Test
    fun coverStyle_outgoingPageHasParallaxAndDimming() {
        val outgoing = PageTurnEffect.transform(0.5f, ReaderPreferences.PAGE_TURN_STYLE_COVER)
        assertEquals(0f, outgoing.rotationY, 0.001f)
        assertTrue(outgoing.translationXFraction < 0f)
        assertTrue(outgoing.alpha < 1f)
        assertTrue(outgoing.scale < 1f)

        val incoming = PageTurnEffect.transform(-0.5f, ReaderPreferences.PAGE_TURN_STYLE_COVER)
        assertEquals(0f, incoming.rotationY, 0.001f)
        assertEquals(1f, incoming.alpha, 0.001f)
        assertEquals(1f, incoming.scale, 0.001f)
        assertEquals(0f, incoming.translationXFraction, 0.001f)
    }

    @Test
    fun noneStyle_isAlwaysIdentity() {
        val t1 = PageTurnEffect.transform(0.7f, ReaderPreferences.PAGE_TURN_STYLE_NONE)
        assertEquals(0f, t1.rotationY, 0.001f)
        assertEquals(1f, t1.alpha, 0.001f)
        assertEquals(1f, t1.scale, 0.001f)
        assertEquals(0f, t1.translationXFraction, 0.001f)

        val t2 = PageTurnEffect.transform(-0.7f, ReaderPreferences.PAGE_TURN_STYLE_NONE)
        assertEquals(0f, t2.rotationY, 0.001f)
        assertEquals(1f, t2.alpha, 0.001f)
        assertEquals(1f, t2.scale, 0.001f)
        assertEquals(0f, t2.translationXFraction, 0.001f)
    }

    @Test
    fun booleanOverload_matchesSimulationAndNone() {
        val enabled = PageTurnEffect.transform(0.5f, true)
        val sim = PageTurnEffect.transform(0.5f, ReaderPreferences.PAGE_TURN_STYLE_SIMULATION)
        assertEquals(sim.rotationY, enabled.rotationY, 0.001f)
        assertEquals(sim.alpha, enabled.alpha, 0.001f)
        assertEquals(sim.scale, enabled.scale, 0.001f)

        val disabled = PageTurnEffect.transform(0.5f, false)
        val none = PageTurnEffect.transform(0.5f, ReaderPreferences.PAGE_TURN_STYLE_NONE)
        assertEquals(none.rotationY, disabled.rotationY, 0.001f)
        assertEquals(none.alpha, disabled.alpha, 0.001f)
        assertEquals(none.scale, disabled.scale, 0.001f)
    }
}
