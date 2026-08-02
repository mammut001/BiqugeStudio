package app.maoyankanshu.novel.selfuse.ui.reader

/**
 * Pure open-phase progress rules for progressive TXT open.
 *
 * While only a first-window body is loaded, or until the full-book page restore has
 * been applied, library progress must stay at the saved [bookPosition]. Page-turn
 * snapshots must not write `progressForPage(stalePagerPage)` (which would clobber
 * mid-book restore and debounce-save 0% / wrong values).
 *
 * Fully JVM unit-testable — no Compose / Android types.
 */
object OpenProgressGate {

    /**
     * Page-turn / pager snapshot may update the 0…1000 library progress only after
     * the full body is loaded **and** restore has been applied to the pager.
     */
    fun mayCommitProgressFromPageTurn(
        textFullyLoaded: Boolean,
        restoreApplied: Boolean,
    ): Boolean = textFullyLoaded && restoreApplied

    /**
     * Progress after a pager page change.
     * When commits are forbidden, returns [heldProgress] unchanged (clamped).
     */
    fun progressAfterPageTurn(
        textFullyLoaded: Boolean,
        restoreApplied: Boolean,
        heldProgress: Int,
        page: Int,
        pageCount: Int,
    ): Int {
        if (!mayCommitProgressFromPageTurn(textFullyLoaded, restoreApplied)) {
            return ProgressMath.clampProgress(heldProgress)
        }
        return PageIndex.progressForPage(page, pageCount)
    }

    /**
     * Target page when full-book approximate (or exact) pagination first becomes active.
     * Always derived from saved library progress — never from a stale window pager index.
     */
    fun restoreTargetPage(savedProgress: Int, pageCount: Int): Int =
        PageIndex.pageForProgress(savedProgress, pageCount)

    /**
     * Which page body to render for approximate paging.
     *
     * Before restore is applied, ignore [pagerPage] (often still 0 from the progressive
     * window) and use [savedProgress] → page so body and footer agree on the first frames.
     */
    fun displayPageForApprox(
        restoreApplied: Boolean,
        pagerPage: Int,
        savedProgress: Int,
        pageCount: Int,
    ): Int {
        if (pageCount <= 0) return 0
        if (!restoreApplied) {
            return restoreTargetPage(savedProgress, pageCount)
        }
        return PageIndex.clampPageIndex(pagerPage, pageCount)
    }

    /**
     * Progressive window → full book swap outcome for open-phase state.
     *
     * @return [SwapRestore] with progress held at saved position and the page the
     * full-book pager must open on.
     */
    fun onFullTextSwap(
        savedProgress: Int,
        fullTextLength: Int,
        charsPerPage: Int,
    ): SwapRestore {
        val held = ProgressMath.clampProgress(savedProgress)
        val count = PageIndex.approximatePageCount(fullTextLength, charsPerPage)
        val targetPage = restoreTargetPage(held, count)
        return SwapRestore(
            heldProgress = held,
            targetPage = targetPage,
            pageCount = count,
            // Until the pager is known to sit on [targetPage], page-turns must not commit.
            restoreApplied = false,
        )
    }

    /**
     * After the full-book pager is positioned at [restoreTargetPage], commits are allowed.
     * Progress stays the held saved value (not recomputed from a possibly-stale page).
     */
    fun afterRestoreApplied(heldProgress: Int): Int =
        ProgressMath.clampProgress(heldProgress)

    data class SwapRestore(
        val heldProgress: Int,
        val targetPage: Int,
        val pageCount: Int,
        val restoreApplied: Boolean,
    )
}
