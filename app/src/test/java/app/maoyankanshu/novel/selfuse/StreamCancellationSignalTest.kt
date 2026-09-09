package app.maoyankanshu.novel.selfuse

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamCancellationSignalTest {
    @Test
    fun outputStopsBeforeNextChunkAfterCancellation() {
        val target = ByteArrayOutputStream()
        val signal = StreamCancellationSignal()
        val output = signal.output(target)
        output.write(byteArrayOf(1, 2, 3))

        signal.cancel()

        try {
            output.write(byteArrayOf(4, 5))
            throw AssertionError("expected cancellation")
        } catch (_: InterruptedIOException) {
            assertArrayEquals(byteArrayOf(1, 2, 3), target.toByteArray())
        }
    }

    @Test
    fun inputStopsBeforeNextReadAfterCancellation() {
        val signal = StreamCancellationSignal()
        val input = signal.input(ByteArrayInputStream(byteArrayOf(7, 8, 9)))
        assertEquals(7, input.read())

        signal.cancel()

        try {
            input.read(ByteArray(2))
            throw AssertionError("expected cancellation")
        } catch (_: InterruptedIOException) {
            assertEquals(2, input.available())
        }
    }
}
