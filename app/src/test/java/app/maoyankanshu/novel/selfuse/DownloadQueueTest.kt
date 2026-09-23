package app.maoyankanshu.novel.selfuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * JVM unit tests for [DownloadQueue].
 */
class DownloadQueueTest {

    @Test
    fun enqueue_onlyAllowsHttps() {
        val queue = DownloadQueue()
        val task = queue.enqueue("https://example.com/book.txt", preferredTitle = "My Book")
        assertEquals("https://example.com/book.txt", task.url)
        assertEquals("My Book", task.preferredTitle)
        assertEquals(DownloadTaskState.Queued, task.state)

        var threw = false
        try {
            queue.enqueue("http://example.com/book.txt")
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue("Plain HTTP must throw IllegalArgumentException", threw)
    }

    @Test
    fun enqueue_duplicateActiveUrlReturnsExisting() {
        val queue = DownloadQueue()
        val task1 = queue.enqueue("https://example.com/book.txt")
        val task2 = queue.enqueue("https://example.com/book.txt")
        assertEquals(task1.id, task2.id)
        assertEquals(1, queue.size())
    }

    @Test
    fun pollNextRunnable_respectsMaxConcurrent() {
        val queue = DownloadQueue(maxConcurrent = 1)
        val task1 = queue.enqueue("https://example.com/book1.txt")
        val task2 = queue.enqueue("https://example.com/book2.txt")

        val next1 = queue.pollNextRunnable()
        assertNotNull(next1)
        assertEquals(task1.id, next1?.id)

        queue.markRunning(task1.id)

        // Running count is now 1, equal to maxConcurrent -> next is null
        val next2 = queue.pollNextRunnable()
        assertNull(next2)

        // Complete task1 -> next2 is now runnable
        queue.markSuccess(task1.id, "b1")
        val next3 = queue.pollNextRunnable()
        assertNotNull(next3)
        assertEquals(task2.id, next3?.id)
    }

    @Test
    fun progressUpdates_andStateTransitions() {
        val queue = DownloadQueue()
        val task = queue.enqueue("https://example.com/test.epub")
        assertTrue(queue.markRunning(task.id, 0L, 1000L))

        var running = queue.getTask(task.id)?.state as? DownloadTaskState.Running
        assertNotNull(running)
        assertEquals(0L, running?.bytesRead)
        assertEquals(1000L, running?.totalBytes)

        assertTrue(queue.updateProgress(task.id, 500L, 1000L, 50000.0))
        running = queue.getTask(task.id)?.state as? DownloadTaskState.Running
        assertEquals(500L, running?.bytesRead)
        assertEquals(50000.0, running?.speedBps ?: 0.0, 0.001)

        assertTrue(queue.markSuccess(task.id, "book_123"))
        val success = queue.getTask(task.id)?.state as? DownloadTaskState.Success
        assertNotNull(success)
        assertEquals("book_123", success?.bookId)
    }

    @Test
    fun retry_onlyAllowsRetryableFailures() {
        val queue = DownloadQueue()
        val retryableTask = queue.enqueue("https://example.com/network_err.txt")
        queue.markRunning(retryableTask.id)
        queue.markFailed(retryableTask.id, IOException("Connection reset"))

        var failedState = queue.getTask(retryableTask.id)?.state as? DownloadTaskState.Failed
        assertNotNull(failedState)
        assertEquals(DownloadImportFailure.Kind.NETWORK, failedState?.detail?.kind)

        // Retry should succeed for network error
        assertTrue(queue.retry(retryableTask.id))
        assertEquals(DownloadTaskState.Queued, queue.getTask(retryableTask.id)?.state)
        assertEquals(1, queue.getTask(retryableTask.id)?.retryCount)

        // Unretryable failure (e.g. 401 Unauthorized)
        val unretryableTask = queue.enqueue("https://example.com/secret.txt")
        queue.markRunning(unretryableTask.id)
        queue.markFailed(unretryableTask.id, HttpStatusException(401))

        val unretryableFailed = queue.getTask(unretryableTask.id)?.state as? DownloadTaskState.Failed
        assertEquals(DownloadImportFailure.Kind.AUTH_REQUIRED, unretryableFailed?.detail?.kind)
        assertFalse("401 must not be retried", queue.retry(unretryableTask.id))
    }

    @Test
    fun cancellation_andClearFinished() {
        val queue = DownloadQueue()
        val task1 = queue.enqueue("https://example.com/cancel1.txt")
        val task2 = queue.enqueue("https://example.com/cancel2.txt")

        assertTrue(queue.cancel(task1.id))
        assertEquals(DownloadTaskState.Cancelled, queue.getTask(task1.id)?.state)

        queue.markRunning(task2.id)
        queue.markSuccess(task2.id, "done_id")

        val cleared = queue.clearFinished()
        assertEquals(2, cleared)
        assertEquals(0, queue.size())
    }

    @Test
    fun shared_isNotNullAndHasDefaultMaxConcurrent() {
        assertNotNull(DownloadQueue.shared)
        assertEquals(1, DownloadQueue.shared.maxConcurrent)
    }
}
