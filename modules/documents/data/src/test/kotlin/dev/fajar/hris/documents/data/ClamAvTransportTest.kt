package dev.fajar.hris.documents.data

import dev.fajar.hris.documents.data.datasources.*
import dev.fajar.hris.documents.data.errors.*
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ClamAvTransportTest {
    private fun settings(f: ClamAvFixture) =
        ClamAvSettings(
            InetSocketAddress("127.0.0.1", f.port),
            responseTimeout = Duration.ofMillis(350),
            totalTimeout = Duration.ofSeconds(3),
        )

    @Test
    fun connectionsHaveBoundedCapacityAndMalformedFramesDoNotLeakPermits() {
        ClamAvFixture().use { fixture ->
            val source = ClamAvDocumentScanDataSource(settings(fixture))
            val first = source.open()
            val second = source.open()
            try {
                assertThrows(DocumentScannerCapacityException::class.java) { source.open() }
            } finally {
                first.close()
                first.close()
                second.close()
            }
            fixture.stream = { _, output ->
                output.write("A".repeat(5000).toByteArray())
                output.flush()
            }
            assertThrows(DocumentScannerProtocolException::class.java) {
                source.open().use { it.finish() }
            }
            fixture.stream = { input, output ->
                fixture.consume(input)
                output.write("stream: OK\u0000".toByteArray())
                output.flush()
            }
            source.open().use {
                it.write(byteArrayOf(1))
                assertTrue(it.finish().clean)
            }
        }
    }

    @Test
    fun tricklingRepliesRespectTheWholeResponseDeadline() {
        ClamAvFixture().use { fixture ->
            fixture.stream = { input, output ->
                fixture.consume(input)
                for (value in "stream: OK\u0000".toByteArray()) {
                    output.write(value.toInt())
                    output.flush()
                    TimeUnit.MILLISECONDS.sleep(100)
                }
            }
            val source = ClamAvDocumentScanDataSource(settings(fixture))
            val started = System.nanoTime()
            assertThrows(java.net.SocketTimeoutException::class.java) {
                source.open().use { it.finish() }
            }
            assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(3))
        }
    }

    @Test
    fun cancelledReadsCloseTheirOwnedConnectionAndReleaseCapacity() {
        ClamAvFixture().use { fixture ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val done = CountDownLatch(1)
            val failure = AtomicReference<Throwable>()
            fixture.stream = { input, _ ->
                fixture.consume(input)
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
            val source =
                ClamAvDocumentScanDataSource(
                    settings(fixture)
                        .copy(
                            responseTimeout = Duration.ofSeconds(5),
                            totalTimeout = Duration.ofSeconds(10),
                            concurrency = 1,
                        )
                )
            Executors.newSingleThreadExecutor().use { pool ->
                val task =
                    pool.submit {
                        try {
                            source.open().use { it.finish() }
                        } catch (error: Throwable) {
                            failure.set(error)
                        } finally {
                            done.countDown()
                        }
                    }
                try {
                    assertTrue(entered.await(3, TimeUnit.SECONDS))
                    task.cancel(true)
                    assertTrue(done.await(2, TimeUnit.SECONDS))
                    assertTrue(
                        failure.get() is InterruptedException,
                        failure.get()?.javaClass?.name,
                    )
                } finally {
                    release.countDown()
                }
            }
            fixture.stream = { input, output ->
                fixture.consume(input)
                output.write("stream: OK\u0000".toByteArray())
                output.flush()
            }
            source.open().use { assertTrue(it.finish().clean) }
        }
    }

    @Test
    fun stalledWritesCannotHoldAWorkerIndefinitely() {
        ClamAvFixture().use { fixture ->
            val release = CountDownLatch(1)
            fixture.stream = { _, _ -> release.await(5, TimeUnit.SECONDS) }
            val source =
                ClamAvDocumentScanDataSource(
                    settings(fixture)
                        .copy(
                            writeTimeout = Duration.ofMillis(250),
                            totalTimeout = Duration.ofSeconds(2),
                        )
                )
            try {
                val started = System.nanoTime()
                assertThrows(java.net.SocketTimeoutException::class.java) {
                    source.open().use { session ->
                        val bytes = ByteArray(1048576)
                        repeat(16) { session.write(bytes) }
                    }
                }
                assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(3))
            } finally {
                release.countDown()
            }
        }
    }
}
