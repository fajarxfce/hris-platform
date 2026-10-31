package dev.fajar.hris.collaboration.data

import com.sun.net.httpserver.HttpServer
import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.domain.policies.collaborationMatrixDeviceId
import java.net.InetSocketAddress
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MatrixSyncTransportTest {
    @Test
    fun longPollHasSeparateCapacityAndInterruptedConnectionReleasesItsSingleDeviceSlot() {
        Fixture().use { f ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            f.handler.set { path ->
                if (path.startsWith("/_matrix/client/v3/sync")) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    "{\"next_batch\":\"s1\"}"
                } else
                    "{\"user_id\":\"@fixture:proof.invalid\",\"device_id\":\"${f.device}\",\"is_guest\":false}"
            }
            val completed = CountDownLatch(1)
            val interrupted = AtomicReference<Throwable>()
            val connection = f.open()
            val thread =
                Thread.ofVirtual().start {
                    try {
                        safeMatrixProviderCall {
                            connection.use { it.sync("s0", "0", 30_000, false) }
                        }
                    } catch (error: Throwable) {
                        interrupted.set(error)
                    } finally {
                        completed.countDown()
                    }
                }
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS))
                assertThrows(MatrixCapacityException::class.java) {
                    connection.sync("s0", "0", 0, false)
                }
                assertThrows(MatrixCapacityException::class.java) { f.open() }
                assertThrows(MatrixCapacityException::class.java) {
                    f.open(collaborationMatrixDeviceId(UUID.randomUUID()))
                }
                val controls =
                    HttpMatrixClientDataSource(f.client.client, f.configuration, Clock.systemUTC())
                assertFalse(controls.whoAmI("fixture-token", Instant.now().plusSeconds(3)).isGuest)
                thread.interrupt()
                assertTrue(completed.await(3, TimeUnit.SECONDS))
                assertInstanceOf(InterruptedException::class.java, interrupted.get())
                f.open().use {}
            } finally {
                release.countDown()
                thread.interrupt()
                thread.join(3000)
                assertFalse(thread.isAlive)
            }
        }
    }

    @Test
    fun deadlinesMalformedAndOversizedBodiesNeverLeakCapacityAndCloseCancelsTheOwner() {
        Fixture().use { f ->
            f.handler.set {
                "{\"padding\":[" +
                    List(2048) { "\"" + "a".repeat(1024) + "\"" }.joinToString(",") +
                    "]}"
            }
            f.open().use { assertEquals(2048, it.sync(null, "0", 0, false).path("padding").size()) }
            for (body in
                listOf(
                    "{\"next_batch\":\"a\",\"next_batch\":\"b\"}",
                    "{} {}",
                    "{\"x\":\"" + "a".repeat(8_388_608) + "\"}",
                )) {
                f.handler.set { body }
                assertThrows(Exception::class.java) {
                    f.open().use { it.sync(null, "0", 0, false) }
                }
                f.open().use {}
            }
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            f.handler.set {
                entered.countDown()
                release.await(3, TimeUnit.SECONDS)
                "{}"
            }
            val finished = CountDownLatch(1)
            val error = AtomicReference<Throwable>()
            val thread =
                Thread.ofVirtual().start {
                    try {
                        safeMatrixProviderCall {
                            f.open().use { it.sync("s0", "0", 30_000, false) }
                        }
                    } catch (failure: Throwable) {
                        error.set(failure)
                    } finally {
                        finished.countDown()
                    }
                }
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                f.source.close()
                assertTrue(finished.await(2, TimeUnit.SECONDS))
                assertInstanceOf(CancellationException::class.java, error.get())
                assertThrows(MatrixCapacityException::class.java) { f.open() }
            } finally {
                release.countDown()
                thread.interrupt()
                thread.join(3000)
            }
        }
        Fixture().use { f ->
            val seen = AtomicReference<String>()
            f.handler.set { path ->
                seen.set(path)
                "{}"
            }
            f.source
                .open(
                    "@fixture:proof.invalid",
                    f.device,
                    "fixture-token",
                    Instant.now().plusMillis(1500),
                )
                .use { it.sync("s0", "0", 30_000, false) }
            assertTrue(seen.get().contains("timeout=0"))
            assertThrows(MatrixDeadlineException::class.java) {
                f.source.open(
                    "@fixture:proof.invalid",
                    f.device,
                    "fixture-token",
                    Instant.now().minusSeconds(1),
                )
            }
        }
    }

    private class Fixture : AutoCloseable {
        val client = MatrixHttpClientOwner()
        private val executor = Executors.newVirtualThreadPerTaskExecutor()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val handler = AtomicReference<(String) -> String>({ "{}" })
        val device = collaborationMatrixDeviceId(UUID.randomUUID())
        val configuration: MatrixProviderConfiguration
        val source: HttpMatrixSyncDataSource

        init {
            server.executor = executor
            server.createContext("/") { request ->
                try {
                    request.requestBody.use { it.readNBytes(3_145_729) }
                    val body = handler.get()(request.requestURI.toASCIIString()).toByteArray()
                    request.responseHeaders.add("Content-Type", "application/json")
                    request.sendResponseHeaders(
                        200,
                        0,
                    ) // Exercise bounded streaming, not Content-Length.
                    request.responseBody.use { it.write(body) }
                } catch (_: java.io.IOException) {
                    /* Owned client cancellation closes the socket. */
                } finally {
                    request.close()
                }
            }
            server.start()
            configuration =
                MatrixProviderConfiguration(
                    URI("http://127.0.0.1:${server.address.port}"),
                    "proof.invalid",
                    "fixture",
                    "fixture",
                    "f".repeat(32),
                )
            source =
                HttpMatrixSyncDataSource(
                    client.client,
                    configuration,
                    Clock.systemUTC(),
                    maximumConcurrent = 1,
                )
        }

        fun open(device: String = this.device) =
            source.open(
                "@fixture:proof.invalid",
                device,
                "fixture-token",
                Instant.now().plusSeconds(10),
            )

        override fun close() {
            source.close()
            server.stop(0)
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            client.close()
        }
    }
}
