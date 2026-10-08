package dev.fajar.hris.core.http

import dev.fajar.hris.core.domain.*
import jakarta.servlet.*
import jakarta.servlet.http.HttpServletRequest
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.mock.web.MockAsyncContext
import org.springframework.mock.web.MockHttpServletResponse

class BinaryResponseWriterTest {
    @Test
    fun timeoutDiscardsLateBytesAndCannotInterruptTheNextReadOnAReusedThread() {
        val first = BinaryFixture()
        val second = BinaryFixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        BinaryResponseWriter(BinaryResponseSettings(concurrency = 1)).use { writer ->
            writer.write(first.request, first.response, ByteRange(0, 2), 3) { _, _ ->
                entered.countDown()
                try {
                    try {
                        release.await(5, TimeUnit.SECONDS)
                    } catch (_: InterruptedException) {
                        check(release.await(5, TimeUnit.SECONDS))
                    }
                    Result.Success(byteArrayOf(1, 2, 3))
                } finally {
                    returned.countDown()
                }
            }
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                first.context.listeners.toList().forEach { it.onTimeout(AsyncEvent(first.context)) }
                writer.write(second.request, second.response, ByteRange(0, 2), 3) { _, _ ->
                    assertFalse(Thread.currentThread().isInterrupted)
                    Result.Success(byteArrayOf(4, 5, 6))
                }
            } finally {
                release.countDown()
            }
            assertTrue(returned.await(2, TimeUnit.SECONDS))
            assertTrue(second.completed.await(2, TimeUnit.SECONDS))
            assertTrue(first.response.bytes().isEmpty())
            assertEquals(504, first.response.status)
            assertArrayEquals(byteArrayOf(4, 5, 6), second.response.bytes())
        }
    }

    @Test
    fun shutdownHasABudgetRejectsNewWorkAndIgnoresLateResults() {
        val fixture = BinaryFixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val thread = AtomicReference<Thread>()
        val writer =
            BinaryResponseWriter(
                BinaryResponseSettings(concurrency = 1, shutdownTimeout = Duration.ofMillis(100))
            )
        writer.write(fixture.request, fixture.response, ByteRange(0, 2), 3) { _, _ ->
            thread.set(Thread.currentThread())
            entered.countDown()
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (release.count > 0 && System.nanoTime() < until) try {
                release.await(100, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                /* Simulates a driver returning late. */
            }
            Result.Success(byteArrayOf(1, 2, 3))
        }
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val start = System.nanoTime()
            writer.close()
            assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2))
            val next = BinaryFixture()
            writer.write(next.request, next.response, ByteRange(0, 2), 3) { _, _ ->
                error("Closed writer must not read")
            }
            assertEquals(503, next.response.status)
        } finally {
            release.countDown()
            writer.close()
        }
        thread.get().join(2000)
        assertFalse(thread.get().isAlive)
        assertTrue(fixture.response.bytes().isEmpty())
    }

    @Test
    fun missingReadProgressEndsTheResponseAndReturnsItsCapacity() {
        BinaryResponseWriter(BinaryResponseSettings(concurrency = 1)).use { writer ->
            val first = BinaryFixture()
            writer.write(first.request, first.response, ByteRange(0, 2), 3) { _, _ ->
                Result.Success(byteArrayOf())
            }
            assertTrue(first.completed.await(2, TimeUnit.SECONDS))
            assertEquals(500, first.response.status)
            val next = BinaryFixture()
            writer.write(next.request, next.response, ByteRange(0, 2), 3) { _, _ ->
                Result.Success(byteArrayOf(1, 2, 3))
            }
            assertTrue(next.completed.await(2, TimeUnit.SECONDS))
            assertArrayEquals(byteArrayOf(1, 2, 3), next.response.bytes())
        }
    }
}

private class BinaryFixture {
    val request = Mockito.mock(HttpServletRequest::class.java)
    val response = RecordingBinaryResponse()
    val context = MockAsyncContext(request, response)
    val completed = CountDownLatch(1)

    init {
        Mockito.`when`(request.startAsync()).thenReturn(context)
        context.addListener(
            object : AsyncListener {
                override fun onComplete(event: AsyncEvent) {
                    completed.countDown()
                }

                override fun onTimeout(event: AsyncEvent) {}

                override fun onError(event: AsyncEvent) {}

                override fun onStartAsync(event: AsyncEvent) {}
            }
        )
    }
}

private class RecordingBinaryResponse : MockHttpServletResponse() {
    private val content = ByteArrayOutputStream()
    private val stream =
        object : ServletOutputStream() {
            override fun isReady() = true

            override fun setWriteListener(listener: WriteListener) {}

            override fun write(value: Int) {
                synchronized(content) { content.write(value) }
            }

            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                synchronized(content) { content.write(bytes, offset, length) }
            }
        }

    override fun getOutputStream(): ServletOutputStream = stream

    fun bytes(): ByteArray = synchronized(content) { content.toByteArray() }
}
