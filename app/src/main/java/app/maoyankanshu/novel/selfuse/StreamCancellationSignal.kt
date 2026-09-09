package app.maoyankanshu.novel.selfuse

import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Cooperative cancellation for blocking SAF streams used by backup/restore/export loops. */
internal class StreamCancellationSignal {
    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
    }

    fun input(delegate: InputStream): InputStream = object : FilterInputStream(delegate) {
        override fun read(): Int {
            throwIfCancelled()
            return super.read()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            throwIfCancelled()
            return super.read(buffer, offset, length)
        }
    }

    fun output(delegate: OutputStream): OutputStream = object : FilterOutputStream(delegate) {
        override fun write(value: Int) {
            throwIfCancelled()
            out.write(value)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            throwIfCancelled()
            out.write(buffer, offset, length)
        }
    }

    private fun throwIfCancelled() {
        if (cancelled.get()) throw InterruptedIOException("stream operation cancelled")
    }
}
