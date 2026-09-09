package app.maoyankanshu.novel.selfuse

import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteImportCancellationSignalTest {
    @Test
    fun cancelDisconnectsAttachedConnection() {
        val connection = FakeConnection()
        val signal = RemoteImportCancellationSignal()

        signal.attach(connection)
        signal.cancel()

        assertTrue(connection.disconnected)
    }

    @Test(expected = InterruptedIOException::class)
    fun attachingAfterCancelDisconnectsAndAborts() {
        val connection = FakeConnection()
        val signal = RemoteImportCancellationSignal()
        signal.cancel()

        try {
            signal.attach(connection)
        } finally {
            assertTrue(connection.disconnected)
        }
    }

    private class FakeConnection : HttpURLConnection(URL("https://example.com/book.txt")) {
        var disconnected: Boolean = false

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false

        override fun connect() = Unit
    }
}
