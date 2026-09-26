package app.maoyankanshu.novel.selfuse

import java.util.UUID

/**
 * State of a single queued download task.
 */
internal sealed class DownloadTaskState {
    data object Queued : DownloadTaskState()
    data class Running(
        val bytesRead: Long = 0L,
        val totalBytes: Long = -1L,
        val speedBps: Double = 0.0,
    ) : DownloadTaskState()
    data class Success(val bookId: String) : DownloadTaskState()
    data class Failed(
        val detail: DownloadImportFailure.Detail,
        val message: String,
    ) : DownloadTaskState()
    data object Cancelled : DownloadTaskState()
}

/**
 * An item in the download queue.
 */
internal data class DownloadTask(
    val id: String,
    val url: String,
    val preferredTitle: String = "",
    val sourceUrl: String = "",
    val state: DownloadTaskState = DownloadTaskState.Queued,
    val enqueuedAtMs: Long,
    val completedAtMs: Long? = null,
    val retryCount: Int = 0,
) {
    val isFinished: Boolean
        get() = when (state) {
            is DownloadTaskState.Success,
            is DownloadTaskState.Cancelled -> true
            is DownloadTaskState.Failed -> !DownloadImportFailure.isRetryUseful(state.detail)
            else -> false
        }

    val isActive: Boolean
        get() = state is DownloadTaskState.Queued || state is DownloadTaskState.Running
}

/**
 * Thread-safe, pure sequential / multi-task download queue.
 *
 * Implements Section 2.2 of the project roadmap (下载队列 / 多任务排队 / 失败重试).
 * Adheres strictly to project privacy & HTTPS standards (no plain http, no scrapers).
 */
internal class DownloadQueue(val maxConcurrent: Int = 1) {
    companion object {
        @JvmStatic
        val shared: DownloadQueue by lazy { DownloadQueue(maxConcurrent = 1) }
    }

    init {
        require(maxConcurrent >= 1) { "maxConcurrent must be >= 1" }
    }

    private val lock = Any()
    private val tasks = mutableListOf<DownloadTask>()

    /**
     * Enqueues a new download for [rawUrl].
     *
     * Validates HTTPS. If a task with the exact normalized URL is already [DownloadTask.isActive],
     * returns that existing active task to prevent duplicate network requests.
     */
    fun enqueue(
        rawUrl: String,
        preferredTitle: String = "",
        sourceUrl: String = "",
        nowMs: Long = System.currentTimeMillis(),
    ): DownloadTask {
        val cleanUrl = rawUrl.trim()
        require(cleanUrl.startsWith("https://", ignoreCase = true)) {
            "Only https:// URLs are supported: $cleanUrl"
        }

        synchronized(lock) {
            val existing = tasks.firstOrNull { it.url == cleanUrl && it.isActive }
            if (existing != null) return existing

            val task = DownloadTask(
                id = UUID.randomUUID().toString(),
                url = cleanUrl,
                preferredTitle = preferredTitle.trim(),
                sourceUrl = sourceUrl.trim(),
                state = DownloadTaskState.Queued,
                enqueuedAtMs = nowMs,
            )
            tasks.add(task)
            return task
        }
    }

    /**
     * Returns the next [DownloadTaskState.Queued] task if current running tasks < [maxConcurrent].
     */
    fun pollNextRunnable(): DownloadTask? = synchronized(lock) {
        val runningCount = tasks.count { it.state is DownloadTaskState.Running }
        if (runningCount >= maxConcurrent) return null
        return tasks.firstOrNull { it.state is DownloadTaskState.Queued }
    }

    fun markRunning(taskId: String, bytesRead: Long = 0L, totalBytes: Long = -1L): Boolean =
        synchronized(lock) {
            val idx = tasks.indexOfFirst { it.id == taskId }
            if (idx < 0) return false
            val current = tasks[idx]
            if (current.state !is DownloadTaskState.Queued && current.state !is DownloadTaskState.Running) {
                return false
            }
            tasks[idx] = current.copy(
                state = DownloadTaskState.Running(bytesRead = bytesRead, totalBytes = totalBytes)
            )
            true
        }

    fun updateProgress(
        taskId: String,
        bytesRead: Long,
        totalBytes: Long,
        speedBps: Double = 0.0,
    ): Boolean = synchronized(lock) {
        val idx = tasks.indexOfFirst { it.id == taskId }
        if (idx < 0) return false
        val current = tasks[idx]
        if (current.state !is DownloadTaskState.Running) return false
        tasks[idx] = current.copy(
            state = DownloadTaskState.Running(
                bytesRead = bytesRead,
                totalBytes = totalBytes,
                speedBps = speedBps,
            )
        )
        true
    }

    fun markSuccess(
        taskId: String,
        bookId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(lock) {
        val idx = tasks.indexOfFirst { it.id == taskId }
        if (idx < 0) return false
        val current = tasks[idx]
        if (current.state !is DownloadTaskState.Running && current.state !is DownloadTaskState.Queued) {
            return false
        }
        tasks[idx] = current.copy(
            state = DownloadTaskState.Success(bookId),
            completedAtMs = nowMs,
        )
        true
    }

    fun markFailed(
        taskId: String,
        error: Throwable,
        message: String = error.message.orEmpty(),
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(lock) {
        val idx = tasks.indexOfFirst { it.id == taskId }
        if (idx < 0) return false
        val current = tasks[idx]
        val detail = DownloadImportFailure.classify(error)
        tasks[idx] = current.copy(
            state = DownloadTaskState.Failed(detail = detail, message = message),
            completedAtMs = nowMs,
        )
        true
    }

    fun cancel(taskId: String): Boolean = synchronized(lock) {
        val idx = tasks.indexOfFirst { it.id == taskId }
        if (idx < 0) return false
        val current = tasks[idx]
        if (current.state is DownloadTaskState.Success) return false
        tasks[idx] = current.copy(
            state = DownloadTaskState.Cancelled,
            completedAtMs = System.currentTimeMillis(),
        )
        true
    }

    /**
     * Retries a previously failed task if [DownloadImportFailure.isRetryUseful] is true.
     * Transitions task back to [DownloadTaskState.Queued] and increments retry count.
     */
    fun retry(taskId: String, nowMs: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        val idx = tasks.indexOfFirst { it.id == taskId }
        if (idx < 0) return false
        val current = tasks[idx]
        val failed = current.state as? DownloadTaskState.Failed ?: return false
        if (!DownloadImportFailure.isRetryUseful(failed.detail)) return false

        tasks[idx] = current.copy(
            state = DownloadTaskState.Queued,
            retryCount = current.retryCount + 1,
            enqueuedAtMs = nowMs,
            completedAtMs = null,
        )
        true
    }

    fun remove(taskId: String): Boolean = synchronized(lock) {
        tasks.removeAll { it.id == taskId }
    }

    fun clearFinished(): Int = synchronized(lock) {
        val before = tasks.size
        tasks.removeAll { it.isFinished }
        before - tasks.size
    }

    fun getTask(taskId: String): DownloadTask? = synchronized(lock) {
        tasks.firstOrNull { it.id == taskId }
    }

    fun getTaskByUrl(url: String): DownloadTask? = synchronized(lock) {
        val clean = url.trim()
        tasks.firstOrNull { it.url == clean }
    }

    fun allTasks(): List<DownloadTask> = synchronized(lock) {
        tasks.toList()
    }

    fun queuedTasks(): List<DownloadTask> = synchronized(lock) {
        tasks.filter { it.state is DownloadTaskState.Queued }
    }

    fun runningTasks(): List<DownloadTask> = synchronized(lock) {
        tasks.filter { it.state is DownloadTaskState.Running }
    }

    fun activeCount(): Int = synchronized(lock) {
        tasks.count { it.isActive }
    }

    fun size(): Int = synchronized(lock) {
        tasks.size
    }
}
