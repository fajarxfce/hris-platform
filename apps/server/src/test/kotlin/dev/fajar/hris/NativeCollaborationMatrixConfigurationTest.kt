package dev.fajar.hris

import dev.fajar.hris.collaboration.data.matrix.MatrixHttpClientOwner
import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixPolicy
import java.net.URI
import java.net.http.*
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource

@TestPropertySource(
    properties =
        [
            "HRIS_COLLABORATION_ENABLED=true",
            "HRIS_MATRIX_ENABLED=true",
            "HRIS_MATRIX_ORIGIN=http://127.0.0.1:9",
            "HRIS_MATRIX_SERVER_NAME=matrix.example.test",
            "HRIS_MATRIX_JWT_ISSUER=urn:hris:matrix:fixture",
            "HRIS_MATRIX_JWT_AUDIENCE=matrix:fixture",
        ]
)
class NativeCollaborationMatrixConfigurationTest : NativeCollaborationApiFixture() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun matrix(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_MATRIX_JWT_SECRET_FILE") {
                Path.of(
                        requireNotNull(
                                NativeCollaborationMatrixConfigurationTest::class
                                    .java
                                    .getResource("/matrix-signing.fixture")
                            )
                            .toURI()
                    )
                    .toString()
            }
        }
    }

    @Autowired private lateinit var owner: MatrixHttpClientOwner
    @Autowired private lateinit var policy: CollaborationMatrixPolicy

    @Test
    fun enabledApiGraphRegistersWithoutWaitingForItsUnavailablePrivateProvider() {
        assertTrue(policy.enabled)
        assertEquals("matrix.example.test", policy.serverName)
        assertFalse(owner.client.isTerminated)
        val n = native()
        val response = register(n)
        assertEquals(200, response.statusCode(), response.body())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_provider_sessions where company_id=?",
                    Int::class.java,
                    n.fixture.company,
                ),
        )
        assertEquals(
            "QUEUED",
            database()
                .queryForObject(
                    "select j.status from collaboration_device_provisioning p join background_jobs j on j.id=p.job_id where p.company_id=?",
                    String::class.java,
                    n.fixture.company,
                ),
        )
    }

    private fun enroll(n: Native): UUID {
        val registered = register(n)
        assertEquals(200, registered.statusCode(), registered.body())
        val device = UUID.fromString(json.readTree(registered.body())["id"].asString())
        // Provider activation has separate actual-worker evidence. This fixture isolates HTTP.
        database()
            .update(
                "update collaboration_devices set state='ACTIVE',version=version+1,updated_at=now() where id=?",
                device,
            )
        return device
    }

    private fun bootstrap(
        n: Native,
        device: UUID,
        operation: UUID? = UUID.randomUUID(),
        extra: Map<String, Any?> = emptyMap(),
    ): HttpResponse<String> {
        val builder =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port${n.path}/bootstrap"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer ${n.access}")
                .header("Content-Type", "application/json")
                .header("X-HRIS-Client-Platform", "ANDROID")
                .header("X-HRIS-Client-Build", "1")
        operation?.let { builder.header("Idempotency-Key", it.toString()) }
        return n.fixture.browser.send(
            builder
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(
                            mapOf(
                                "deviceId" to device,
                                "expectedDeviceVersion" to 1,
                                "expectedGatewayVersion" to null,
                            ) + extra
                        )
                    )
                )
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun matrixRequest(
        n: Native,
        body: String,
        contentType: String = "application/json",
        chunked: Boolean = false,
        authorization: String? = null,
    ): HttpResponse<String> {
        val builder =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/_matrix/client/v3/refresh"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", contentType)
        authorization?.let { builder.header("Authorization", it) }
        val publisher =
            if (chunked) HttpRequest.BodyPublishers.ofInputStream { body.byteInputStream() }
            else HttpRequest.BodyPublishers.ofString(body)
        return n.fixture.browser.send(
            builder.POST(publisher).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun matrixRefresh(n: Native, token: String, authorization: String? = null) =
        matrixRequest(
            n,
            json.writeValueAsString(mapOf("refresh_token" to token)),
            authorization = authorization,
        )

    private fun status(n: Native): HttpResponse<String> =
        n.fixture.browser.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port${n.path}"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer ${n.access}")
                .header("X-HRIS-Client-Platform", "ANDROID")
                .header("X-HRIS-Client-Build", "1")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun nativeBootstrapReturnsOnlyGatewayCredentialsAndStatusSupportsVersionedRecovery() {
        val n = native()
        val device = enroll(n)
        val operation = UUID.randomUUID()
        val first = bootstrap(n, device, operation)
        assertEquals(200, first.statusCode(), first.body())
        assertEquals("no-store", first.headers().firstValue("Cache-Control").orElse(""))
        assertEquals(first.body(), bootstrap(n, device, operation).body())
        val body = json.readTree(first.body())
        assertTrue(body["accessToken"].asString().startsWith("hris_mx_a_"))
        assertTrue(body["refreshToken"].asString().startsWith("hris_mx_r_"))
        assertEquals(
            "HRIS_" + device.toString().replace("-", ""),
            body["matrixDeviceId"].asString(),
        )
        assertTrue(body["matrixUserId"].asString().endsWith(":matrix.example.test"))
        assertEquals(
            setOf(
                "sessionId",
                "version",
                "matrixUserId",
                "matrixDeviceId",
                "accessToken",
                "refreshToken",
                "accessExpiresAt",
                "expiresAt",
            ),
            body.propertyNames().toSet(),
        )
        val current = status(n)
        assertEquals(200, current.statusCode(), current.body())
        val gateway = json.readTree(current.body())["device"]["gateway"]
        assertEquals(body["sessionId"].asString(), gateway["id"].asString())
        assertEquals(
            setOf("id", "version", "clientBuild", "accessExpiresAt", "expiresAt", "revokedAt"),
            gateway.propertyNames().toSet(),
        )
        assertFalse(current.body().contains(body["accessToken"].asString()))
        assertFalse(current.body().contains(body["refreshToken"].asString()))
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_provider_sessions where company_id=?",
                    Int::class.java,
                    n.fixture.company,
                ),
        )
    }

    @Test
    fun standardMatrixRefreshRotatesAndReplaysWithoutNativeBearerOrCookieFallback() {
        val n = native()
        val first = bootstrap(n, enroll(n))
        assertEquals(200, first.statusCode(), first.body())
        val token = json.readTree(first.body())["refreshToken"].asString()
        val early = matrixRefresh(n, token)
        assertEquals(429, early.statusCode(), early.body())
        assertEquals("M_LIMIT_EXCEEDED", json.readTree(early.body())["errcode"].asString())
        assertEquals(10000, json.readTree(early.body())["retry_after_ms"].asInt())
        clock.set(clock.instant().plusSeconds(11))
        val next = matrixRefresh(n, token, "Bearer deliberately-not-a-native-session")
        assertEquals(200, next.statusCode(), next.body())
        assertEquals("no-store", next.headers().firstValue("Cache-Control").orElse(""))
        val body = json.readTree(next.body())
        assertEquals(
            setOf("access_token", "refresh_token", "expires_in_ms"),
            body.propertyNames().toSet(),
        )
        assertEquals(300000, body["expires_in_ms"].asInt())
        assertEquals(next.body(), matrixRefresh(n, token).body())
        assertEquals(1, json.readTree(status(n).body())["device"]["gateway"]["version"].asInt())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                n.fixture.company,
                n.fixture.account,
            )
        val denied = matrixRefresh(n, token, "Bearer ${n.access}")
        assertEquals(403, denied.statusCode(), denied.body())
        assertEquals("M_FORBIDDEN", json.readTree(denied.body())["errcode"].asString())
        assertEquals(
            "access_denied",
            json.readTree(denied.body())["org.hris.error_code"].asString(),
        )
    }

    @Test
    fun matrixRefreshRejectsMalformedDuplicateTrailingDeepAndOversizedBodies() {
        val n = native()
        for (body in
            listOf(
                "",
                "{}",
                "[]",
                "{\"refresh_token\":1}",
                "{\"refresh_token\":\"a\",\"accountId\":\"b\"}",
                "{\"refresh_token\":\"a\",\"refresh_token\":\"b\"}",
                "{\"refresh_token\":\"a\"} {}",
                "[[[[[[]]]]]]",
            )) {
            val response = matrixRequest(n, body)
            assertEquals(
                400,
                response.statusCode(),
                "Invalid fixture shape returned ${response.statusCode()}",
            )
            assertEquals("M_BAD_JSON", json.readTree(response.body())["errcode"].asString())
        }
        assertEquals(415, matrixRequest(n, "{}", "text/plain").statusCode())
        for (chunked in listOf(false, true)) {
            val response = matrixRequest(n, " ".repeat(2049), chunked = chunked)
            assertEquals(413, response.statusCode(), response.body())
            assertEquals("M_TOO_LARGE", json.readTree(response.body())["errcode"].asString())
        }
        val invalid = matrixRefresh(n, n.access, "Bearer ${n.access}")
        assertEquals(401, invalid.statusCode(), invalid.body())
        val problem = json.readTree(invalid.body())
        assertEquals("M_UNKNOWN_TOKEN", problem["errcode"].asString())
        assertTrue(problem["soft_logout"].asBoolean())
    }

    @Test
    fun bootstrapRequiresNativeVersionsAndTheGatewayDeniesEveryUnimplementedProviderPath() {
        val n = native()
        val device = enroll(n)
        failure(
            command(
                n.fixture.browser,
                n.path + "/bootstrap",
                json.writeValueAsString(
                    mapOf(
                        "deviceId" to device,
                        "expectedDeviceVersion" to 1,
                        "expectedGatewayVersion" to null,
                    )
                ),
                n.fixture.csrf,
                UUID.randomUUID(),
            ),
            403,
            "invalid_session_transport",
        )
        assertEquals(400, bootstrap(n, device, operation = null).statusCode())
        assertEquals(
            400,
            bootstrap(n, device, extra = mapOf("nativeSessionId" to UUID.randomUUID())).statusCode(),
        )
        failure(
            bootstrap(n, device, extra = mapOf("expectedDeviceVersion" to 0)),
            409,
            "collaboration_gateway_changed",
        )
        for (path in
            listOf(
                "/_matrix/client/v3/login",
                "/_matrix/client/v3/logout",
                "/_matrix/client/v3/sync",
                "/_matrix/federation/v1/version",
                "/_synapse/admin/v2/users",
                "/_matrix/client/v3/refresh",
            )) {
            val response =
                n.fixture.browser.send(
                    HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer ${n.access}")
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertEquals(403, response.statusCode(), response.body())
            assertEquals("M_FORBIDDEN", json.readTree(response.body())["errcode"].asString())
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
        }
    }
}
