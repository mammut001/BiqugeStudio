package app.maoyankanshu.novel.selfuse.ui.reader

import kotlin.math.absoluteValue

/**
 * Visual parameters for a left/right book-style page turn.
 *
 * [pageOffset] is the same convention as Compose [androidx.compose.foundation.pager.PagerState]:
 * `(currentPage - page) + currentPageOffsetFraction`.
 * Settled on-page → `0`; swiping toward next → negative for the next page, positive for the current.
 */
data class PageTurnTransform(
    /** Degrees around the Y axis (perspective flip). */
    val rotationY: Float,
    /** 0 = left edge pivot (incoming from right), 1 = right edge pivot (leaving left). */
    val pivotFractionX: Float,
    val alpha: Float,
    val scale: Float,
)

/**
 * Pure math for horizontal page-turn look. Applied via [androidx.compose.ui.graphics.graphicsLayer]
 * on each pager page so swipe and [animateScrollToPage] taps share the same effect.
 */
object PageTurnEffect {
    /** Peak tilt in degrees at full page offset (readable, not a full 90° card). */
    const val MAX_ROTATION_DEG: Float = 72f
    private const val MAX_SCALE_SHRINK: Float = 0.05f
    private const val MAX_ALPHA_FADE: Float = 0.18f

    fun transform(pageOffset: Float): PageTurnTransform =
        transform(pageOffset, animationEnabled = true)

    /**
     * @param animationEnabled when false, returns identity transform (flat cross-fade free).
     */
    fun transform(pageOffset: Float, animationEnabled: Boolean): PageTurnTransform {
        if (!animationEnabled) {
            return PageTurnTransform(
                rotationY = 0f,
                pivotFractionX = 0.5f,
                alpha = 1f,
                scale = 1f,
            )
        }
        val clamped = pageOffset.coerceIn(-1f, 1f)
        val abs = clamped.absoluteValue
        val scale = (1f - abs * MAX_SCALE_SHRINK).coerceIn(0.9f, 1f)
        val alpha = (1f - abs * MAX_ALPHA_FADE).coerceIn(0.75f, 1f)
        return if (clamped > 0f) {
            // Current/previous page leaving toward the left → pivot on right edge.
            PageTurnTransform(
                rotationY = abs * MAX_ROTATION_DEG,
                pivotFractionX = 1f,
                alpha = alpha,
                scale = scale,
            )
        } else if (clamped < 0f) {
            // Next page entering from the right → pivot on left edge.
            PageTurnTransform(
                rotationY = -abs * MAX_ROTATION_DEG,
                pivotFractionX = 0f,
                alpha = alpha,
                scale = scale,
            )
        } else {
            PageTurnTransform(
                rotationY = 0f,
                pivotFractionX = 0.5f,
                alpha = 1f,
                scale = 1f,
            )
        }
    }
}
