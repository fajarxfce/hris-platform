package dev.fajar.hris.collaboration.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationMatrixPusherRepository
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
import tools.jackson.databind.json.JsonMapper

class MatrixPusherTransportTest {
    private val key = "hriscp1_" + "a".repeat(64)

    @Test
    fun registrationAndRemovalKeepFixedRoutesOpaqueKeysAndEventOnlyPayloads() {
        Fixture().use { f ->
            val bodies = CopyOnWriteArrayList<String>()
            f.handler.set {
                assertEquals("POST", it.requestMethod)
                assertEquals("/_matrix/client/v3/pushers/set", it.requestURI.path)
                assertNull(it.requestURI.rawQuery)
                assertEquals("Bearer fixture-bearer", it.requestHeaders.getFirst("Authorization"))
                assertEquals("identity", it.requestHeaders.getFirst("Accept-Encoding"))
                bodies.add(String(it.requestBody.readNBytes(8193)))
                f.reply(it)
            }
            repeat(2) {
                assertEquals(Result.Success(Unit), f.repo.set(key, "fixture-bearer", f.deadline()))
            }
            repeat(2) {
                assertEquals(
                    Result.Success(Unit),
                    f.repo.remove(key, "fixture-bearer", f.deadline()),
                )
            }
            assertEquals(bodies[0], bodies[1])
            assertEquals(bodies[2], bodies[3])
            val set = f.json.readTree(bodies[0])
            assertEquals(
                setOf(
                    "app_id",
                    "pushkey",
                    "kind",
                    "append",
                    "app_display_name",
                    "device_display_name",
                    "lang",
                    "data",
                ),
                set.propertyNames(),
            )
            assertEquals("dev.fajar.hris.chat", set["app_id"].asString())
            assertEquals(key, set["pushkey"].asString())
            assertEquals("http", set["kind"].asString())
            assertTrue(set["append"].asBoolean())
            assertEquals("HRIS", set["app_display_name"].asString())
            assertEquals("HRIS", set["device_display_name"].asString())
            assertEquals("en", set["lang"].asString())
            assertEquals(setOf("url", "format"), set["data"].propertyNames())
            assertEquals(f.gateway.endpoint.toASCIIString(), set["data"]["url"].asString())
            assertEquals("event_id_only", set["data"]["format"].asString())
            val removed = f.json.readTree(bodies[2])
            assertEquals(setOf("app_id", "pushkey", "kind"), removed.propertyNames())
            assertTrue(removed["kind"].isNull)
            assertEquals(key, removed["pushkey"].asString())
            assertEquals("dev.fajar.hris.chat", removed["app_id"].asString())
            assertEquals(4, f.requests.get())
        }
    }

    @Test
    fun unsafeGatewayConfigurationAndNonOpaqueKeysNeverReachTheProvider() {
        for (value in
            listOf(
                "http://push.example.test/_matrix/push/v1/notify",
                "https://private:secret@push.example.test/_matrix/push/v1/notify",
                "https://push.example.test/other",
                "https://push.example.test/_matrix/push/v1/notify?target=private",
                "https://push.example.test/_matrix/push/v1/notify#private",
                "https://push.example.test:0/_matrix/push/v1/notify",
                "https://push.example.test:65536/_matrix/push/v1/notify",
                "https://push.example.test/_matrix/push/v1/%6eotify",
            )) {
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    MatrixPushGatewayConfiguration(URI(value))
                }
            assertEquals("Invalid Matrix push gateway endpoint", error.message)
        }
        Fixture().use { f ->
            for (value in
                listOf(
                    "",
                    "native-fcm-token",
                    key + "0",
                    key.dropLast(1),
                    key.replace('a', 'A'),
                    "private\nkey",
                )) {
                assertInstanceOf(
                    Result.Failed::class.java,
                    f.repo.set(value, "fixture-bearer", f.deadline()),
                )
                assertInstanceOf(
                    Result.Failed::class.java,
                    f.repo.remove(value, "fixture-bearer", f.deadline()),
                )
            }
            assertEquals(0, f.requests.get())
            assertEquals("MatrixPushGatewayConfiguration(<redacted>)", f.gateway.toString())
        }
    }

    @Test
    fun providerFailuresAreBoundedAndNeverAutomaticallyRetriedOrRedirected() {
        Fixture().use { f ->
            val invalid =
                listOf("[]", "{\"unexpected\":true}", "{}{}", "{\"a\":1,\"a\":2}", "x".repeat(8193))
            for (body in invalid) {
                f.handler.set { f.reply(it, body) }
                assertEquals(
                    "collaboration_provider_invalid_response",
                    (f.repo.set(key, "fixture-bearer", f.deadline()) as Result.Failed).failure.code,
                )
            }
            for ((status, code) in
                listOf(
                    307 to "collaboration_provider_rejected",
                    401 to "collaboration_provider_rejected",
                    429 to "collaboration_provider_busy",
                    503 to "collaboration_provider_unavailable",
                )) {
                f.handler.set {
                    it.responseHeaders.set("Location", "https://elsewhere.invalid/private")
                    f.reply(it, "private provider error", status)
                }
                assertEquals(
                    code,
                    (f.repo.set(key, "fixture-bearer", f.deadline()) as Result.Failed).failure.code,
                )
            }
            assertEquals(invalid.size + 4, f.requests.get())
        }
    }

    @Test
    fun expiredAdmissionAndOversizedRequestsDoNotSendWhileInterruptedBodiesReleaseCapacity() {
        Fixture().use { f ->
            assertEquals(
                "collaboration_admission_changed",
                (f.repo.set(key, "fixture-bearer", Instant.EPOCH) as Result.Failed).failure.code,
            )
            assertThrows(MatrixResponseException::class.java) {
                f.source.setPusher(
                    mapOf("data" to "x".repeat(8193)),
                    "fixture-bearer",
                    f.deadline(),
                )
            }
            assertEquals(0, f.requests.get())
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val ended = CountDownLatch(1)
            val interrupted = AtomicReference<Boolean>(false)
            f.handler.set {
                it.responseHeaders.set("Content-Type", "application/json")
                it.sendResponseHeaders(200, 0)
                it.responseBody.flush()
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            val owner = Executors.newSingleThreadExecutor()
            try {
                val pending =
                    owner.submit {
                        try {
                            f.repo.set(key, "fixture-bearer", f.deadline())
                        } catch (_: InterruptedException) {
                            interrupted.set(true)
                        } finally {
                            ended.countDown()
                        }
                    }
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                assertEquals(
                    "collaboration_provider_busy",
                    (f.repo.remove(key, "fixture-bearer", f.deadline()) as Result.Failed)
                        .failure
                        .code,
                )
                pending.cancel(true)
                assertTrue(ended.await(2, TimeUnit.SECONDS))
                assertTrue(interrupted.get())
                release.countDown()
                f.handler.set { f.reply(it) }
                assertEquals(Result.Success(Unit), f.repo.set(key, "fixture-bearer", f.deadline()))
                assertEquals(2, f.requests.get())
            } finally {
                release.countDown()
                owner.shutdownNow()
                assertTrue(owner.awaitTermination(5, TimeUnit.SECONDS))
            }
        }
    }

    private class Fixture : AutoCloseable {
        val json = JsonMapper.builder().build()
        val requests = AtomicInteger()
        val handler = AtomicReference<(HttpExchange) -> Unit>()
        private val workers = Executors.newFixedThreadPool(2)
        private val server =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 2).apply {
                executor = workers
                createContext("/") { exchange ->
                    exchange.use {
                        requests.incrementAndGet()
                        handler.get().invoke(it)
                    }
                }
                start()
            }
        private val client =
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
        val source =
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
        val gateway =
            MatrixPushGatewayConfiguration(URI("https://push.example.test/_matrix/push/v1/notify"))
        val repo = InternalCollaborationMatrixPusherRepository(source, gateway)

        fun deadline() = Instant.now().plusSeconds(10)

        fun reply(exchange: HttpExchange, body: String = "{}", status: Int = 200) {
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
        }
    }
}
