package app.maoyankanshu.novel.selfuse

/**
 * Ownership tokens for overlapping cancellation/finally edges in import screens.
 *
 * Cancelling deliberately makes the UI idle immediately so another import may start. The
 * cancelled coroutine can still reach `catch` / `finally` afterwards; only the current token
 * may mutate shared Compose state.
 */
internal class ImportSessionTracker {
    private var generation: Long = 0L

    fun start(): Long {
        generation = if (generation == Long.MAX_VALUE) 1L else generation + 1L
        return generation
    }

    fun invalidate() {
        generation = if (generation == Long.MAX_VALUE) 1L else generation + 1L
    }

    fun owns(token: Long): Boolean = token == generation
}
