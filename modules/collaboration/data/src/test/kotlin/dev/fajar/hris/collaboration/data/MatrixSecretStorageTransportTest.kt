package dev.fajar.hris.collaboration.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationSecretStorageRepository
import dev.fajar.hris.collaboration.domain.entities.CollaborationSecretStorageDefaultKey
import dev.fajar.hris.core.domain.Result
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.time.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MatrixSecretStorageTransportTest {
    @Test
    fun readsExactOwnEventAndPreservesMissingDisabledAndConfiguredValues() {
        Fixture().use { f ->
            for ((status, body, expected) in
                listOf(
                    Triple(404, "{\"errcode\":\"M_NOT_FOUND\"}", null),
                    Triple(200, "{}", CollaborationSecretStorageDefaultKey(null)),
                    Triple(
                        200,
                        "{\"key\":\"fixture-key\"}",
                        CollaborationSecretStorageDefaultKey("fixture-key"),
                    ),
                )) {
                f.handler.set {
                    assertEquals("GET", it.requestMethod)
                    assertEquals(
                        "/_matrix/client/v3/user/@fixture:matrix.example.test/account_data/m.secret_storage.default_key",
                        it.requestURI.path,
                    )
                    assertEquals(null, it.requestURI.rawQuery)
                    assertEquals(
                        "Bearer fixture-token",
                        it.requestHeaders.getFirst("Authorization"),
                    )
                    f.reply(it, body, status)
                }
                assertEquals(Result.Success(expected), f.read())
            }
            assertEquals(3, f.requests.get())
        }
    }

    @Test
    fun errorsAndMalformedOrOversizedResponsesNeverBecomeMissingAndNeverRetry() {
        Fixture().use { f ->
            val invalid =
                listOf(
                    "[]",
                    "null",
                    "{\"key\":null}",
                    "{\"key\":1}",
                    "{\"key\":\"\"}",
                    "{\"key\":\"bad key\"}",
                    "{\"key\":\"${"a".repeat(256)}\"}",
                    "{\"key\":\"a\",\"key\":\"b\"}",
                    "{\"secret\":\"private\"}",
                    "{}{}",
                    " ".repeat(4097),
                )
            for (body in invalid) {
                f.handler.set { f.reply(it, body) }
                assertEquals(
                    "collaboration_provider_invalid_response",
                    (f.read() as Result.Failed).failure.code,
                )
            }
            for (status in listOf(307, 401, 403, 404, 429, 503)) {
                f.handler.set {
                    it.responseHeaders.set("Location", "https://elsewhere.invalid/private")
                    f.reply(it, "{\"errcode\":\"M_FORBIDDEN\"}", status)
                }
                assertInstanceOf(Result.Failed::class.java, f.read())
            }
            assertEquals(invalid.size + 6, f.requests.get())
        }
    }

    @Test
    fun deadlineDoesNotSendAndInterruptedBodyReleasesSingleExchangePermit() {
        Fixture().use { f ->
            assertEquals(
                "collaboration_admission_changed",
                (f.repo.defaultKey("@fixture:matrix.example.test", "fixture-token", Instant.EPOCH)
                        as Result.Failed)
                    .failure
                    .code,
            )
            assertEquals(0, f.requests.get())
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val ended = CountDownLatch(1)
            val interrupted = AtomicReference(false)
            f.handler.set {
                it.responseHeaders.set("Content-Type", "application/json")
                it.sendResponseHeaders(200, 0)
                it.responseBody.flush()
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            val owner = Executors.newSingleThreadExecutor()
            try {
                val work =
                    owner.submit {
                        try {
                            f.read()
                        } catch (_: InterruptedException) {
                            interrupted.set(true)
                        } finally {
                            ended.countDown()
                        }
                    }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                work.cancel(true)
                assertTrue(ended.await(5, TimeUnit.SECONDS))
                assertTrue(interrupted.get())
                release.countDown()
                f.handler.set { f.reply(it, "{}") }
                assertEquals(Result.Success(CollaborationSecretStorageDefaultKey(null)), f.read())
            } finally {
                release.countDown()
                owner.shutdownNow()
                assertTrue(owner.awaitTermination(5, TimeUnit.SECONDS))
            }
        }
    }

    private class Fixture : AutoCloseable {
        val requests = AtomicInteger()
        val handler = AtomicReference<(HttpExchange) -> Unit>()
        private val workers = Executors.newFixedThreadPool(2)
        private val errors = ConcurrentLinkedQueue<Throwable>()
        private val server =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 2).apply {
                executor = workers
                createContext("/") { exchange ->
                    exchange.use {
                        requests.incrementAndGet()
                        try {
                            handler.get().invoke(it)
                        } catch (failure: Throwable) {
                            errors.add(failure)
                        }
                    }
                }
                start()
            }
        private val client =
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
        val repo =
            InternalCollaborationSecretStorageRepository(
                HttpMatrixClientDataSource(
                    client,
                    MatrixProviderConfiguration(
                        URI("http://127.0.0.1:${server.address.port}"),
                        "matrix.example.test",
                        "fixture",
                        "fixture",
                        "x".repeat(32),
                    ),
                    Clock.systemUTC(),
                    maximumConcurrent = 1,
                )
            )

        fun read() =
            repo.defaultKey(
                "@fixture:matrix.example.test",
                "fixture-token",
                Instant.now().plusSeconds(10),
            )

        fun reply(exchange: HttpExchange, body: String, status: Int = 200) {
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, 0)
            exchange.responseBody.write(body.toByteArray())
        }

        override fun close() {
            client.shutdownNow()
            server.stop(0)
            workers.shutdownNow()
            assertTrue(client.awaitTermination(Duration.ofSeconds(5)))
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))
            assertTrue(errors.isEmpty(), "Owned HTTP fixture failed")
        }
    }
}
