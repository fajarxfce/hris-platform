package dev.fajar.hris.storage.data.datasources

import dev.fajar.hris.storage.data.errors.InvalidObjectStorageResponse
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import software.amazon.awssdk.http.Abortable

/** Bounds XML before SDK parsing and abandons unread bytes instead of draining on close. */
class BoundedInventoryInputStream(
    input: InputStream,
    private val abortable: Abortable,
    private val maximumBytes: Int = 524288,
) : FilterInputStream(input) {
    init {
        require(maximumBytes in 1..524288)
    }

    private var consumed = 0
    private val closed = java.util.concurrent.atomic.AtomicBoolean()

    override fun read(): Int {
        if (closed.get()) throw IOException("Inventory stream closed")
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val value = `in`.read()
        if (value != -1 && ++consumed > maximumBytes) {
            close()
            throw InvalidObjectStorageResponse()
        }
        return value
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        java.util.Objects.checkFromIndexSize(offset, length, bytes.size)
        if (length == 0) return 0
        if (closed.get()) throw IOException("Inventory stream closed")
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        val read = `in`.read(bytes, offset, minOf(length, maximumBytes - consumed + 1))
        if (read == 0) {
            close()
            throw InvalidObjectStorageResponse()
        }
        if (read > 0) consumed += read
        if (consumed > maximumBytes) {
            close()
            throw InvalidObjectStorageResponse()
        }
        return read
    }

    override fun skip(length: Long): Long {
        if (length <= 0) return 0
        val bytes = ByteArray(minOf(length, 8192L).toInt())
        return read(bytes, 0, bytes.size).coerceAtLeast(0).toLong()
    }

    override fun available(): Int =
        if (closed.get()) 0 else minOf(`in`.available(), maximumBytes - consumed + 1)

    override fun markSupported(): Boolean = false

    override fun mark(readlimit: Int) = Unit

    override fun reset(): Unit = throw IOException("Inventory stream cannot reset")

    override fun close() {
        if (closed.compareAndSet(false, true)) abortable.abort()
    }
}
