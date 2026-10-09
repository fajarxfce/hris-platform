package dev.fajar.hris.push.data

import com.google.auth.oauth2.ServiceAccountCredentials
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.push.data.datasources.*
import dev.fajar.hris.push.data.repositories.FcmPushRepository
import dev.fajar.hris.push.data.transport.GooglePushAuthTransport
import dev.fajar.hris.push.domain.entities.*
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.security.KeyPairGenerator
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class FcmPushRepositoryTest {
    private val json = JsonMapper.builder().build()
    private val now = Instant.parse("2026-10-01T00:00:00Z")
    private lateinit var server: HttpServer
    private lateinit var workers: ExecutorService
    private lateinit var client: HttpClient
    private lateinit var endpoint: URI
    private val received = AtomicInteger()
    private val handler = AtomicReference<(HttpExchange) -> Unit>()

    @BeforeEach
    fun start() {
        workers = Executors.newFixedThreadPool(2)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 2)
        server.executor = workers
        server.createContext("/") { exchange ->
            exchange.use {
                received.incrementAndGet()
                handler.get().invoke(it)
            }
        }
        server.start()
        endpoint = URI("http://127.0.0.1:${server.address.port}/messages:send")
        client =
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
    }

    @AfterEach
    fun close() {
        client.shutdownNow()
        assertTrue(client.awaitTermination(Duration.ofSeconds(5)))
        server.stop(0)
        workers.shutdownNow()
        assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))
    }

    private fun reply(exchange: HttpExchange, status: Int, body: String, chunked: Boolean = false) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, if (chunked) 0 else bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    private fun repository(
        timeout: Duration = Duration.ofSeconds(2),
        credentials: PushCredentialDataSource = PushCredentialDataSource { "test-google-access" },
    ) =
        FcmPushRepository(
            credentials,
            FcmPushDataSource(client, endpoint, timeout),
            json,
            Clock.fixed(now, ZoneOffset.UTC),
        )

    private fun message() =
        PushMessage(
            "fixture-target-token",
            mapOf(
                "eventCode" to "communications.inbox_available",
                "companyId" to "test-company",
                "inboxId" to "test-inbox",
            ),
            now.plusSeconds(300),
        )

    private fun oauth(): GooglePushCredentialDataSource {
        val key =
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private
        val credentials =
            ServiceAccountCredentials.newBuilder()
                .setClientEmail("fixture@project.iam.gserviceaccount.com")
                .setPrivateKey(key)
                .setScopes(listOf("https://www.googleapis.com/auth/firebase.messaging"))
                .setTokenServerUri(endpoint)
                .setHttpTransportFactory {
                    GooglePushAuthTransport(client, endpoint, Duration.ofSeconds(2))
                }
                .setDefaultRetriesEnabled(false)
                .build()
        return GooglePushCredentialDataSource(credentials)
    }

    @Test
    fun payloadUsesDataOnlyHintsAndFiniteAndroidAndAppleLifetimes() {
        val body = AtomicReference<String>()
        handler.set { exchange ->
            assertEquals(
                "Bearer test-google-access",
                exchange.requestHeaders.getFirst("Authorization"),
            )
            body.set(String(exchange.requestBody.readNBytes(8193)))
            reply(exchange, 200, """{"name":"projects/test/messages/accepted"}""")
        }
        assertEquals(Result.Success(PushOutcome.ACCEPTED), repository().send(message()))
        val sent = json.readTree(body.get())["message"]
        assertEquals("fixture-target-token", sent["token"].asString())
        assertNull(sent.get("notification"))
        assertEquals("300s", sent["android"]["ttl"].asString())
        assertEquals("NORMAL", sent["android"]["priority"].asString())
        assertEquals("background", sent["apns"]["headers"]["apns-push-type"].asString())
        assertEquals("5", sent["apns"]["headers"]["apns-priority"].asString())
        assertEquals(1, sent["apns"]["payload"]["aps"]["content-available"].asInt())
        assertEquals(
            now.plusSeconds(300).epochSecond.toString(),
            sent["apns"]["headers"]["apns-expiration"].asString(),
        )
        assertEquals(1, received.get())
    }

    @Test
    fun onlyExplicitUnregisteredResponsesRetireTokens() {
        val outcomes =
            listOf(
                Triple(404, "UNREGISTERED", Result.Success(PushOutcome.UNREGISTERED)),
                Triple(
                    400,
                    "INVALID_ARGUMENT",
                    Result.Failed(Failure(FailureKind.UNEXPECTED, "push_delivery_rejected")),
                ),
                Triple(
                    403,
                    "SENDER_ID_MISMATCH",
                    Result.Failed(Failure(FailureKind.UNEXPECTED, "push_sender_mismatch")),
                ),
                Triple(
                    404,
                    "NOT_FOUND",
                    Result.Failed(Failure(FailureKind.UNEXPECTED, "push_delivery_rejected")),
                ),
            )
        for ((status, code, expected) in outcomes) {
            handler.set {
                reply(
                    it,
                    status,
                    """{"error":{"message":"private target detail","details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError","errorCode":"$code"}]}}""",
                )
            }
            assertEquals(expected, repository().send(message()))
        }
        handler.set {
            reply(
                it,
                404,
                """{"error":{"details":[{"@type":"untrusted","errorCode":"UNREGISTERED"}]}}""",
            )
        }
        val result = repository().send(message())
        assertTrue(result is Result.Failed)
        assertFalse(result.toString().contains("private target detail"))
    }

    @Test
    fun transientAndAuthenticationFailuresAreMappedWithoutAutomaticSendRetries() {
        for ((status, code) in
            listOf(
                503 to "push_delivery_unavailable",
                408 to "push_delivery_unavailable",
                401 to "push_credentials_rejected",
            )) {
            handler.set { reply(it, status, "provider body intentionally not JSON") }
            val before = received.get()
            val result = repository().send(message()) as Result.Failed
            assertEquals(code, result.failure.code)
            assertEquals(before + 1, received.get())
        }
        handler.set {
            it.responseHeaders.add("Retry-After", "120")
            reply(it, 429, "{}")
        }
        assertEquals(
            Result.Failed(
                Failure(
                    FailureKind.RATE_LIMITED,
                    "push_rate_limited",
                    parameters = mapOf("retryAfterSeconds" to "120"),
                )
            ),
            repository().send(message()),
        )
        handler.set {
            it.responseHeaders.add("Retry-After", "untrusted detail")
            reply(it, 429, "{}")
        }
        assertTrue((repository().send(message()) as Result.Failed).failure.parameters.isEmpty())
    }

    @Test
    fun redirectsAndOversizedChunkedResponsesNeverEscapeTransportBounds() {
        handler.set {
            it.responseHeaders.add("Location", endpoint.toString())
            reply(it, 307, "{}")
        }
        assertEquals(
            "push_delivery_rejected",
            (repository().send(message()) as Result.Failed).failure.code,
        )
        assertEquals(1, received.get())
        handler.set { reply(it, 200, "x".repeat(70000), chunked = true) }
        val large = repository().send(message()) as Result.Failed
        assertEquals("push_response_invalid", large.failure.code)
        assertEquals(2, received.get())
    }

    @Test
    fun incompleteResponseTimesOutAndOwnedClientsTerminate() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        handler.set { exchange ->
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            reply(exchange, 200, "{}")
        }
        try {
            val result = repository(Duration.ofMillis(150)).send(message()) as Result.Failed
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertEquals("push_delivery_unavailable", result.failure.code)
            assertEquals(1, received.get())
        } finally {
            release.countDown()
        }
    }

    @Test
    fun stalledResponseBodiesAreAlsoBoundedByTheRequestDeadline() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        handler.set { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, 100)
            exchange.responseBody.write('{'.code)
            exchange.responseBody.flush()
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        Executors.newSingleThreadExecutor().use { executor ->
            val pending =
                executor.submit<Result<PushOutcome>> {
                    repository(Duration.ofMillis(150)).send(message())
                }
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                val result = pending.get(2, TimeUnit.SECONDS)
                assertEquals("push_delivery_unavailable", (result as Result.Failed).failure.code)
            } finally {
                pending.cancel(true)
                release.countDown()
            }
        }
    }

    @Test
    fun googleOAuthUsesOneSynchronousRefreshAcrossConcurrentCallsAndDisablesRetry() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        handler.set { exchange ->
            assertEquals("identity", exchange.requestHeaders.getFirst("Accept-Encoding"))
            val request = String(exchange.requestBody.readNBytes(65537))
            assertTrue(request.contains("assertion="))
            assertTrue(request.contains("grant_type="))
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            reply(
                exchange,
                200,
                """{"access_token":"test-oauth-token","expires_in":3600,"token_type":"Bearer"}""",
            )
        }
        val credentials = oauth()
        Executors.newFixedThreadPool(2).use { executor ->
            val futures = (1..2).map { executor.submit<String> { credentials.accessToken() } }
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS))
            } finally {
                release.countDown()
            }
            assertEquals(
                listOf("test-oauth-token", "test-oauth-token"),
                futures.map { it.get(5, TimeUnit.SECONDS) },
            )
        }
        assertEquals(1, received.get())
        handler.set { reply(it, 503, "{}") }
        val failed = safePushCall { Result.Success(oauth().accessToken()) }
        assertEquals("push_delivery_unavailable", (failed as Result.Failed).failure.code)
        assertEquals(2, received.get())
    }

    @Test
    fun sdkRefreshAndSendCancellationPropagateWithoutFailureConversion() {
        val preserved = Result.Failed(Failure(FailureKind.CONFLICT, "preserved"))
        assertSame(preserved, safePushCall { preserved })
        assertThrows(CancellationException::class.java) {
            safePushCall<Nothing> { throw java.io.IOException(CancellationException()) }
        }
        assertThrows(InterruptedException::class.java) {
            try {
                Thread.currentThread().interrupt()
                repository().send(message())
            } finally {
                Thread.interrupted()
            }
        }
        assertEquals(0, received.get())
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        handler.set {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            reply(it, 200, "{}")
        }
        Executors.newSingleThreadExecutor().use { executor ->
            val running =
                executor.submit<Unit> {
                    try {
                        repository().send(message())
                        fail<Unit>("Cancellation must propagate")
                    } catch (_: InterruptedException) {
                        stopped.countDown()
                    }
                }
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                running.cancel(true)
                assertTrue(stopped.await(2, TimeUnit.SECONDS))
            } finally {
                release.countDown()
            }
        }
    }
}
