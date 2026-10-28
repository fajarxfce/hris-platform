package dev.fajar.hris.identity.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.fajar.hris.identity.data.firebase.datasources.GoogleFirebaseKeyDataSource
import dev.fajar.hris.identity.data.firebase.errors.FirebaseKeysUnavailableException
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.time.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class FirebaseKeyTransportTest {
    @Test
    fun concurrentRequestsShareFreshKeysAndFailuresCannotCreateARetryStorm() {
        val f = Fixture()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val calls = List(4) { pool.submit(Callable { f.source.keys() }) }
            calls.forEach { assertEquals(setOf("test-key"), it.get(8, TimeUnit.SECONDS).keys) }
            assertEquals(1, f.requests.get())
            f.clock.now = f.clock.now.plusSeconds(61)
            f.handler = { exchange -> f.respond(exchange, "{}", 503) }
            repeat(2) {
                assertThrows(FirebaseKeysUnavailableException::class.java) { f.source.keys() }
            }
            assertEquals(2, f.requests.get())
            f.clock.now = f.clock.now.plusSeconds(30)
            f.handler = { exchange -> f.respond(exchange, f.body) }
            assertEquals(setOf("test-key"), f.source.keys().keys)
            assertEquals(3, f.requests.get())
        } finally {
            pool.shutdownNow()
            f.close()
        }
    }

    @Test
    fun streamingOversizeEncodingAndMalformedKeysNeverReplaceTheCache() {
        for (mode in 0..3) {
            val f = Fixture()
            try {
                f.handler = { exchange ->
                    when (mode) {
                        0 -> f.respond(exchange, " ".repeat(65537))
                        1 -> {
                            exchange.responseHeaders.set("Content-Encoding", "gzip")
                            f.respond(exchange, f.body)
                        }
                        2 -> f.respond(exchange, "{\"test-key\":\"not a certificate\"}")
                        3 -> f.respond(exchange, "{\"test-key\":null}")
                    }
                }
                assertThrows(FirebaseKeysUnavailableException::class.java) { f.source.keys() }
                assertThrows(FirebaseKeysUnavailableException::class.java) { f.source.keys() }
                assertEquals(1, f.requests.get())
            } finally {
                f.close()
            }
        }
    }

    @Test
    fun theOwnerTimesOutBodyReceptionAndDoesNotKeepItsKeyLock() {
        val f = Fixture()
        val release = CountDownLatch(1)
        try {
            f.handler = { exchange ->
                f.headers(exchange)
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.flush()
                release.await(10, TimeUnit.SECONDS)
            }
            assertTimeoutPreemptively(Duration.ofSeconds(8)) {
                assertThrows(FirebaseKeysUnavailableException::class.java) { f.source.keys() }
            }
            assertThrows(FirebaseKeysUnavailableException::class.java) { f.source.keys() }
            assertEquals(1, f.requests.get())
        } finally {
            release.countDown()
            f.close()
        }
    }

    @Test
    fun interruptionCancelsAcquisitionAndReleasesTheCacheOwner() {
        val f = Fixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        try {
            f.handler = { exchange ->
                f.headers(exchange)
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.flush()
                entered.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
            val operation =
                pool.submit {
                    try {
                        assertThrows(InterruptedException::class.java) { f.source.keys() }
                    } finally {
                        finished.countDown()
                    }
                }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            operation.cancel(true)
            assertTrue(finished.await(3, TimeUnit.SECONDS))
            assertTimeoutPreemptively(Duration.ofSeconds(2)) {
                assertThrows(FirebaseKeysUnavailableException::class.java) { f.source.keys() }
            }
            assertEquals(1, f.requests.get())
        } finally {
            release.countDown()
            pool.shutdownNow()
            f.close()
        }
    }

    private class MutableClock : Clock() {
        var now = Instant.parse("2030-01-01T00:00:00Z")

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }

    private class Fixture : AutoCloseable {
        val clock = MutableClock()
        val requests = AtomicInteger()
        val body =
            JsonMapper.builder()
                .build()
                .writeValueAsString(
                    mapOf(
                        "test-key" to
                            requireNotNull(
                                    javaClass.getResource("/firebase/public-certificate.txt")
                                )
                                .readText()
                    )
                )
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newFixedThreadPool(2)
        val client =
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
        var handler: (HttpExchange) -> Unit = { respond(it, body) }
        val source: GoogleFirebaseKeyDataSource

        init {
            server.executor = executor
            server.createContext("/keys") { exchange ->
                requests.incrementAndGet()
                try {
                    handler(exchange)
                } catch (_: java.io.IOException) {
                    /* The client may cancel reception. */
                } finally {
                    exchange.close()
                }
            }
            server.start()
            source =
                GoogleFirebaseKeyDataSource(
                    client,
                    clock,
                    URI("http://127.0.0.1:${server.address.port}/keys"),
                )
        }

        fun headers(exchange: HttpExchange) {
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.responseHeaders.set("Cache-Control", "public, max-age=60, must-revalidate")
        }

        fun respond(exchange: HttpExchange, body: String, status: Int = 200) {
            headers(exchange)
            exchange.sendResponseHeaders(status, 0)
            exchange.responseBody.write(body.toByteArray())
        }

        override fun close() {
            client.shutdownNow()
            server.stop(0)
            executor.shutdownNow()
        }
    }
}
