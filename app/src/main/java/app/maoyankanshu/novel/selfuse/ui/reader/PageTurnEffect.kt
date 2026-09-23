package app.maoyankanshu.novel.selfuse.ui.reader

import app.maoyankanshu.novel.selfuse.ReaderPreferences
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
    /** Relative horizontal translation fraction (0 = default pager positioning). */
    val translationXFraction: Float = 0f,
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
        transform(pageOffset, ReaderPreferences.PAGE_TURN_STYLE_SIMULATION)

    /**
     * Legacy boolean overload: when false returns [ReaderPreferences.PAGE_TURN_STYLE_NONE],
     * when true returns [ReaderPreferences.PAGE_TURN_STYLE_SIMULATION].
     */
    fun transform(pageOffset: Float, animationEnabled: Boolean): PageTurnTransform =
        if (animationEnabled) {
            transform(pageOffset, ReaderPreferences.PAGE_TURN_STYLE_SIMULATION)
        } else {
            transform(pageOffset, ReaderPreferences.PAGE_TURN_STYLE_NONE)
        }

    /**
     * Calculates page turn transform for the specified [pageTurnStyle]
     * (SIMULATION, SLIDE, COVER, NONE).
     */
    fun transform(pageOffset: Float, style: Int): PageTurnTransform {
        val clamped = pageOffset.coerceIn(-1f, 1f)
        val abs = clamped.absoluteValue

        return when (style) {
            ReaderPreferences.PAGE_TURN_STYLE_SIMULATION -> {
                if (abs == 0f) {
                    PageTurnTransform(
                        rotationY = 0f,
                        pivotFractionX = 0.5f,
                        alpha = 1f,
                        scale = 1f,
                    )
                } else {
                    val scale = (1f - abs * MAX_SCALE_SHRINK).coerceIn(0.9f, 1f)
                    val alpha = (1f - abs * MAX_ALPHA_FADE).coerceIn(0.75f, 1f)
                    if (clamped > 0f) {
                        // Leaving toward the left → pivot on right edge
                        PageTurnTransform(
                            rotationY = abs * MAX_ROTATION_DEG,
                            pivotFractionX = 1f,
                            alpha = alpha,
                            scale = scale,
                        )
                    } else {
                        // Entering from the right → pivot on left edge
                        PageTurnTransform(
                            rotationY = -abs * MAX_ROTATION_DEG,
                            pivotFractionX = 0f,
                            alpha = alpha,
                            scale = scale,
                        )
                    }
                }
            }
            ReaderPreferences.PAGE_TURN_STYLE_SLIDE -> {
                // Pure horizontal slide: no 3D tilt
                PageTurnTransform(
                    rotationY = 0f,
                    pivotFractionX = 0.5f,
                    alpha = 1f,
                    scale = 1f,
                )
            }
            ReaderPreferences.PAGE_TURN_STYLE_COVER -> {
                // Cover turn: current/leaving page recedes with subtle shadow and slight parallax,
                // while incoming page glides over it.
                if (clamped > 0f) {
                    PageTurnTransform(
                        rotationY = 0f,
                        pivotFractionX = 0.5f,
                        alpha = (1f - abs * 0.22f).coerceIn(0.75f, 1f),
                        scale = (1f - abs * 0.04f).coerceIn(0.95f, 1f),
                        translationXFraction = -clamped * 0.25f,
                    )
                } else {
                    PageTurnTransform(
                        rotationY = 0f,
                        pivotFractionX = 0.5f,
                        alpha = 1f,
                        scale = 1f,
                        translationXFraction = 0f,
                    )
                }
            }
            ReaderPreferences.PAGE_TURN_STYLE_NONE -> {
                PageTurnTransform(
                    rotationY = 0f,
                    pivotFractionX = 0.5f,
                    alpha = 1f,
                    scale = 1f,
                )
            }
            else -> transform(pageOffset, ReaderPreferences.PAGE_TURN_STYLE_SIMULATION)
        }
    }
}
