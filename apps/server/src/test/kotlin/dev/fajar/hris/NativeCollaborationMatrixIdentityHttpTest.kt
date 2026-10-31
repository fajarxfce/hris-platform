package dev.fajar.hris

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.*
import java.nio.file.Path
import java.time.Duration
import java.util.Base64
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.json.JsonMapper

/** Actual API/SQL/security graph; the private provider is a bounded owned HTTP fixture. */
@TestPropertySource(
    properties =
        [
            "HRIS_COLLABORATION_ENABLED=true",
            "HRIS_MATRIX_ENABLED=true",
            "HRIS_MATRIX_SERVER_NAME=matrix.example.test",
            "HRIS_MATRIX_JWT_ISSUER=urn:hris:matrix:fixture",
            "HRIS_MATRIX_JWT_AUDIENCE=matrix:fixture",
        ]
)
class NativeCollaborationMatrixIdentityHttpTest : NativeCollaborationApiFixture() {
    companion object {
        private val provider = Provider()

        @JvmStatic
        @DynamicPropertySource
        fun matrix(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_MATRIX_ORIGIN") {
                "http://127.0.0.1:${provider.server.address.port}"
            }
            registry.add("HRIS_MATRIX_JWT_SECRET_FILE") {
                Path.of(
                        requireNotNull(
                                NativeCollaborationMatrixIdentityHttpTest::class
                                    .java
                                    .getResource("/matrix-signing.fixture")
                            )
                            .toURI()
                    )
                    .toString()
            }
        }

        @JvmStatic @AfterAll fun stopProvider() = provider.close()
    }

    private fun enroll(n: Native): Pair<UUID, String> {
        val registered = register(n)
        assertEquals(200, registered.statusCode(), registered.body())
        val device = UUID.fromString(json.readTree(registered.body())["id"].asString())
        // Actual provisioning is covered by the separate worker/provider integration case.
        database()
            .update(
                "update collaboration_devices set state='ACTIVE',version=version+1,updated_at=now() where id=?",
                device,
            )
        val response =
            n.fixture.browser.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port${n.path}/bootstrap"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer ${n.access}")
                    .header("Content-Type", "application/json")
                    .header("X-HRIS-Client-Platform", "ANDROID")
                    .header("X-HRIS-Client-Build", "1")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            json.writeValueAsString(
                                mapOf(
                                    "deviceId" to device,
                                    "expectedDeviceVersion" to 1,
                                    "expectedGatewayVersion" to null,
                                )
                            )
                        )
                    )
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, response.statusCode(), response.body())
        return device to json.readTree(response.body())["accessToken"].asString()
    }

    private fun identity(
        n: Native,
        token: String? = null,
        suffix: String = "",
        duplicate: Boolean = false,
        body: HttpRequest.BodyPublisher? = null,
    ): HttpResponse<String> {
        val request =
            HttpRequest.newBuilder(
                    URI("http://127.0.0.1:$port/_matrix/client/v3/account/whoami$suffix")
                )
                .timeout(Duration.ofSeconds(15))
        token?.let { request.header("Authorization", "Bearer $it") }
        if (duplicate) request.header("Authorization", "Bearer another-token")
        if (body == null) request.GET() else request.method("GET", body)
        return n.fixture.browser.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun identityUsesOnlyGatewayAuthorityAndReturnsValidatedPublicMetadata() {
        val n = native()
        val (device, token) = enroll(n)
        val before = provider.logins.get()
        repeat(2) {
            val response = identity(n, token)
            assertEquals(200, response.statusCode(), response.body())
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
            val result = json.readTree(response.body())
            assertEquals(setOf("user_id", "device_id", "is_guest"), result.propertyNames().toSet())
            assertEquals(
                "@hris_${n.fixture.company.toString().replace("-", "")}_${n.fixture.account.toString().replace("-", "")}:matrix.example.test",
                result["user_id"].asString(),
            )
            assertEquals(
                "HRIS_${device.toString().replace("-", "")}",
                result["device_id"].asString(),
            )
            assertFalse(result["is_guest"].asBoolean())
            assertFalse(response.body().contains(token))
        }
        assertEquals(before + 1, provider.logins.get())
        val queries = provider.queries.get()
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                n.fixture.company,
                n.fixture.account,
            )
        val denied = identity(n, token)
        assertEquals(403, denied.statusCode(), denied.body())
        assertEquals("M_FORBIDDEN", json.readTree(denied.body())["errcode"].asString())
        assertEquals(queries, provider.queries.get())
    }

    @Test
    fun identityRejectsNativeCookieDuplicateUrlAndBodyCredentialsBeforeProviderAccess() {
        val n = native()
        val (_, token) = enroll(n)
        val queries = provider.queries.get()
        val logins = provider.logins.get()
        for (candidate in listOf(null, n.access, "invalid")) {
            val response = identity(n, candidate)
            assertEquals(401, response.statusCode(), response.body())
            assertEquals("M_UNKNOWN_TOKEN", json.readTree(response.body())["errcode"].asString())
            assertTrue(json.readTree(response.body())["soft_logout"].asBoolean())
        }
        assertEquals(401, identity(n, token, duplicate = true).statusCode())
        assertEquals(
            400,
            identity(n, token, suffix = "?access_token=not-a-credential").statusCode(),
        )
        for (body in
            listOf(
                HttpRequest.BodyPublishers.ofString("{}"),
                HttpRequest.BodyPublishers.ofInputStream { "{}".byteInputStream() },
            )) assertEquals(400, identity(n, token, body = body).statusCode())
        assertEquals(queries, provider.queries.get())
        assertEquals(logins, provider.logins.get())
    }

    @Test
    fun identityWithholdsLateProviderResultWhenItsNativeParentIsRevoked() {
        val n = native()
        val (_, token) = enroll(n)
        provider.onQuery.set {
            // Acquires the same native row lock: the HTTP exchange must hold no SQL guard.
            database()
                .update(
                    "update native_sessions set revoked_at=now(),version=version+1 where id=?",
                    n.session,
                )
        }
        try {
            val response = identity(n, token)
            assertEquals(401, response.statusCode(), response.body())
            assertEquals("M_UNKNOWN_TOKEN", json.readTree(response.body())["errcode"].asString())
            assertFalse(response.body().contains("user_id"))
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select count(*) from collaboration_provider_sessions where company_id=?",
                        Int::class.java,
                        n.fixture.company,
                    ),
            )
        } finally {
            provider.onQuery.set(null)
        }
    }

    private class Provider : AutoCloseable {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val logins = AtomicInteger()
        val queries = AtomicInteger()
        val onQuery = AtomicReference<(() -> Unit)?>(null)
        private val credentials = ConcurrentHashMap<String, Map<String, Any>>()
        private val executor = Executors.newFixedThreadPool(2)
        private val parser = JsonMapper.builder().build()
        private val errors = ConcurrentLinkedQueue<Throwable>()

        init {
            server.executor = executor
            server.createContext("/") { request ->
                try {
                    val response =
                        when (request.requestURI.path) {
                            "/_matrix/client/v3/login" -> {
                                logins.incrementAndGet()
                                val body = parser.readTree(request.requestBody.readNBytes(8192))
                                val payload =
                                    parser.readTree(
                                        Base64.getUrlDecoder()
                                            .decode(body["token"].asString().split('.')[1])
                                    )
                                val token = "fictional-${UUID.randomUUID()}"
                                val identity =
                                    mapOf(
                                        "user_id" to
                                            "@${payload["sub"].asString()}:matrix.example.test",
                                        "device_id" to body["device_id"].asString(),
                                        "is_guest" to false,
                                    )
                                credentials[token] = identity
                                identity + mapOf("access_token" to token, "expires_in_ms" to 600000)
                            }
                            "/_matrix/client/v3/account/whoami" -> {
                                queries.incrementAndGet()
                                onQuery.getAndSet(null)?.invoke()
                                requireNotNull(
                                    credentials[
                                        request.requestHeaders
                                            .getFirst("Authorization")
                                            .removePrefix("Bearer ")]
                                )
                            }
                            else -> error("Unexpected fixed provider route")
                        }
                    val bytes = parser.writeValueAsBytes(response)
                    request.responseHeaders.add("Content-Type", "application/json")
                    request.sendResponseHeaders(200, bytes.size.toLong())
                    request.responseBody.write(bytes)
                } catch (error: Throwable) {
                    errors.add(error)
                } finally {
                    request.close()
                }
            }
            server.start()
        }

        override fun close() {
            server.stop(0)
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            assertTrue(errors.isEmpty(), "Owned provider fixture failed")
        }
    }
}
