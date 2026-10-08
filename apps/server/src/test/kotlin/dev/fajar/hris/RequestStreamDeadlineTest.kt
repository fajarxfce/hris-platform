package dev.fajar.hris

import dev.fajar.hris.core.http.BoundedRequestStream
import dev.fajar.hris.core.http.RequestBodyTimeoutException
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RequestStreamDeadlineTest {
    @Test
    fun progressCannotExtendTheAbsoluteBodyBudgetAndLateBytesAreRejected() {
        val time = AtomicLong()
        var reads = 0
        var closed = false
        val source =
            object : ServletInputStream() {
                override fun read(): Int {
                    reads++
                    time.addAndGet(Duration.ofMillis(110).toNanos())
                    return 1
                }

                override fun isFinished() = false

                override fun isReady() = true

                override fun setReadListener(listener: ReadListener) {}

                override fun close() {
                    closed = true
                }
            }
        BoundedRequestStream(source, 100, Duration.ofMillis(250), time::get).use { stream ->
            assertEquals(1, stream.read())
            assertEquals(1, stream.read())
            assertThrows(RequestBodyTimeoutException::class.java) { stream.read() }
            assertThrows(RequestBodyTimeoutException::class.java) { stream.read() }
            assertEquals(3, reads)
        }
        assertTrue(closed)
    }

    @Test
    fun lateBulkCompletionCannotEscapeTheBodyDeadline() {
        val time = AtomicLong()
        val source =
            object : ServletInputStream() {
                override fun read() = -1

                override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                    time.set(Duration.ofSeconds(1).toNanos())
                    bytes[offset] = 1
                    return 1
                }

                override fun isFinished() = false

                override fun isReady() = true

                override fun setReadListener(listener: ReadListener) {}
            }
        BoundedRequestStream(source, 100, Duration.ofMillis(100), time::get).use { stream ->
            assertThrows(RequestBodyTimeoutException::class.java) { stream.read(ByteArray(10)) }
        }
    }
}
