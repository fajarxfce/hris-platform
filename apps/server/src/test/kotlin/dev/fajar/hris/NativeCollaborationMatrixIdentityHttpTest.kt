package dev.fajar.hris

import com.sun.net.httpserver.HttpServer
import dev.fajar.hris.collaboration.delivery.requests.matrixKeyUploadRequest
import dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceKeyPublication
import dev.fajar.hris.collaboration.domain.policies.validateCollaborationDeviceKeys
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceKeyRepository
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.core.domain.TransactionRunner
import dev.fajar.hris.core.domain.flatMap
import dev.fajar.hris.core.domain.map
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
            "HRIS_COLLABORATION_SPACE_ENABLED=true",
            "HRIS_COLLABORATION_SPACE_MIN_ANDROID_BUILD=1",
            "HRIS_COLLABORATION_PUSH_ENABLED=true",
            "HRIS_COLLABORATION_PUSH_CALLBACK_ENABLED=true",
            "HRIS_MATRIX_PUSH_CALLBACK_SOURCES=127.0.0.1/32",
            "HRIS_MATRIX_PUSH_GATEWAY_URL=https://push.example.test/_matrix/push/v1/notify",
            "HRIS_MATRIX_SERVER_NAME=matrix.example.test",
            "HRIS_MATRIX_JWT_ISSUER=urn:hris:matrix:fixture",
            "HRIS_MATRIX_JWT_AUDIENCE=matrix:fixture",
        ]
)
class NativeCollaborationMatrixIdentityHttpTest : NativeCollaborationApiFixture() {
    private val ownedParents = mutableSetOf<UUID>()
    @Autowired private lateinit var groupKeyTransactions: TransactionRunner
    @Autowired private lateinit var groupKeyPublications: CollaborationSpaceKeyRepository

    @BeforeEach
    fun alignProtocolClockWithCurrentDatabaseTime() {
        // This live eligibility view uses database time. The broader People fixture
        // intentionally uses October 1; that is not a live protocol parent today.
        clock.followSystemTime()
    }

    @AfterEach
    fun retireOwnedNativeParents() {
        for (parent in ownedParents) database()
            .update(
                "update native_sessions set revoked_at=coalesce(revoked_at,now()),version=version+1 where id=?",
                parent,
            )
    }

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

    @Test
    fun privatePushCallbackCommitsBeforeReplyAndKeepsTemporaryDisablementRecoverable() {
        val n = native()
        val (device, gateway) = enroll(n)
        savePush(n)
        assertEquals(200, pusher(n, gateway).statusCode())
        val key = provider.pushes.last()["pushkey"].asString()
        val unknown = "hriscp1_" + "a".repeat(64)
        val body = callbackBody(listOf(key, unknown))
        val response = callback(body)
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
        assertEquals(
            listOf(unknown),
            json.readTree(response.body())["rejected"].toList().map { it.asString() },
        )
        fun revision() =
            database()
                .queryForObject(
                    "select revision from collaboration_push_hints where company_id=? and device_id=?",
                    Long::class.java,
                    n.fixture.company,
                    device,
                )
        assertEquals(1L, revision())
        repeat(2) { assertEquals(response.body(), callback(body).body()) }
        assertEquals(1L, revision())
        savePush(n, expected = 0, enabled = false)
        assertEquals("{\"rejected\":[]}", callback(callbackBody(listOf(key))).body())
        assertEquals(1L, revision())
        savePush(n, expected = 1)
        clock.set(clock.instant().plusSeconds(901))
        assertEquals(200, callback(callbackBody(listOf(key))).statusCode())
        assertEquals(1L, revision())
        database()
            .update(
                "update native_sessions set revoked_at=now(),version=version+1 where id=?",
                n.session,
            )
        val retired = callback(callbackBody(listOf(key)))
        assertEquals(200, retired.statusCode())
        assertEquals(key, json.readTree(retired.body())["rejected"][0].asString())
    }

    @Test
    fun privatePushCallbackRejectsASpoofedForwardedPeerBeforeReadingItsBody() {
        java.net.Socket().use { socket ->
            socket.bind(InetSocketAddress("127.0.0.2", 0))
            socket.connect(InetSocketAddress("127.0.0.1", port), 2000)
            socket.soTimeout = 2000
            socket
                .getOutputStream()
                .write(
                    ("POST /_matrix/push/v1/notify HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: 8192\r\nX-Forwarded-For: 127.0.0.1\r\nForwarded: for=127.0.0.1;proto=https\r\nAuthorization: Bearer fixture\r\nCookie: SESSION=fixture\r\nConnection: close\r\n\r\n")
                        .toByteArray()
                )
            socket.getOutputStream().flush()
            // No body bytes are ever supplied. Authentication must reject before body acquisition.
            val status = socket.getInputStream().bufferedReader().readLine()
            assertTrue(status.startsWith("HTTP/1.1 403"), status)
        }
    }

    @Test
    fun privatePushCallbackRejectsMalformedCompressedQueriedAndOversizedInput() {
        for (body in
            listOf(
                "{}",
                "[]",
                "{\"notification\":{},\"notification\":{}}",
                callbackBody(listOf("hriscp1_" + "b".repeat(64))) + " {}",
            )) assertEquals(400, callback(body).statusCode())
        val valid = callbackBody(listOf("hriscp1_" + "b".repeat(64)))
        assertEquals(415, callback(valid, contentType = "text/plain").statusCode())
        assertEquals(415, callback(valid, encoding = "gzip").statusCode())
        assertEquals(400, callback(valid, suffix = "?source=trusted").statusCode())
        for (chunked in listOf(false, true)) assertEquals(
            413,
            callback(" ".repeat(8193), chunked = chunked).statusCode(),
        )
        assertEquals(200, callback(valid).statusCode())
    }

    @Test
    fun privatePushCallbackStalledSocketIsBoundedAndTheNextRequestStillWorks() {
        val started = System.nanoTime()
        java.net.Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 2000)
            socket.soTimeout = 7000
            socket
                .getOutputStream()
                .write(
                    ("POST /_matrix/push/v1/notify HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: 100\r\nConnection: close\r\n\r\n{")
                        .toByteArray()
                )
            socket.getOutputStream().flush()
            val status = socket.getInputStream().bufferedReader().readLine()
            assertTrue(status.startsWith("HTTP/1.1 408"), status)
        }
        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(7))
        assertEquals(200, callback(callbackBody(listOf("hriscp1_" + "c".repeat(64)))).statusCode())
    }

    private fun callbackBody(keys: List<String>) =
        json.writeValueAsString(
            mapOf(
                "notification" to
                    mapOf(
                        "counts" to mapOf("unread" to 1),
                        "id" to "",
                        "type" to null,
                        "sender" to "",
                        "devices" to
                            keys.map {
                                mapOf(
                                    "app_id" to "dev.fajar.hris.chat",
                                    "pushkey" to it,
                                    "data" to mapOf("format" to "event_id_only"),
                                )
                            },
                    )
            )
        )

    private fun callback(
        body: String,
        contentType: String = "application/json",
        encoding: String? = null,
        suffix: String = "",
        chunked: Boolean = false,
    ): HttpResponse<String> {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().use { client ->
            val request =
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/_matrix/push/v1/notify$suffix"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", contentType)
            encoding?.let { request.header("Content-Encoding", it) }
            val publisher =
                if (chunked) HttpRequest.BodyPublishers.ofInputStream { body.byteInputStream() }
                else HttpRequest.BodyPublishers.ofString(body)
            return client.send(
                request.POST(publisher).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        }
    }

    @Test
    fun managedPusherUsesExistingNativeRegistrationAndKeepsSecretTargetsServerSide() {
        val n = native()
        val (device, gateway) = enroll(n)
        val before = provider.pushes.size
        val missing = pusher(n, gateway)
        assertEquals(503, missing.statusCode())
        assertEquals(
            "collaboration_push_unavailable",
            json.readTree(missing.body())["org.hris.error_code"].asString(),
        )
        assertEquals(before, provider.pushes.size)
        savePush(n)
        repeat(2) {
            val response = pusher(n, gateway)
            assertEquals(200, response.statusCode(), response.body())
            assertEquals("{}", response.body())
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
        }
        savePush(n, expected = 0)
        assertEquals(200, pusher(n, gateway).statusCode())
        val sent = provider.pushes.toList().drop(before)
        assertEquals(3, sent.size)
        assertEquals(1, sent.map { it["pushkey"].asString() }.toSet().size)
        assertTrue(sent.all { it["pushkey"].asString().matches(Regex("hriscp1_[a-f0-9]{64}")) })
        assertTrue(
            sent.all {
                it["app_id"].asString() == "dev.fajar.hris.chat" && it["kind"].asString() == "http"
            }
        )
        assertTrue(
            sent.all {
                it["data"]["format"].asString() == "event_id_only" &&
                    it["data"]["url"].asString() ==
                        "https://push.example.test/_matrix/push/v1/notify"
            }
        )
        assertEquals(
            1L,
            database()
                .queryForObject(
                    "select version from collaboration_push_bindings where company_id=? and device_id=?",
                    Long::class.java,
                    n.fixture.company,
                    device,
                ),
        )
    }

    @Test
    fun managedPusherRejectsCookieNativeBearerCallerTargetsAndAlternateProtocolRoutes() {
        val n = native()
        val (_, gateway) = enroll(n)
        savePush(n)
        val before = provider.pushes.size
        assertEquals(401, pusher(n).statusCode())
        assertEquals(401, pusher(n, n.access).statusCode())
        assertEquals(401, pusher(n, gateway, duplicate = true).statusCode())
        assertEquals(400, pusher(n, gateway, suffix = "?access_token=untrusted").statusCode())
        assertEquals(
            400,
            pusher(
                    n,
                    gateway,
                    body =
                        "{\"pushkey\":\"untrusted\",\"data\":{\"url\":\"https://elsewhere.invalid\"}}",
                )
                .statusCode(),
        )
        assertEquals(403, pusher(n, gateway, method = "GET").statusCode())
        val standard =
            n.fixture.browser.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/_matrix/client/v3/pushers/set"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer $gateway")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(403, standard.statusCode())
        assertEquals(before, provider.pushes.size)
    }

    @Test
    fun temporaryNativePushDisablementBlocksActivationAndReenableReusesTheSamePusher() {
        val n = native()
        val (_, gateway) = enroll(n)
        savePush(n)
        assertEquals(200, pusher(n, gateway).statusCode())
        val original = provider.pushes.last()["pushkey"].asString()
        val before = provider.pushes.size
        savePush(n, expected = 0, enabled = false)
        assertEquals(503, pusher(n, gateway).statusCode())
        assertEquals(before, provider.pushes.size)
        savePush(n, expected = 1)
        assertEquals(200, pusher(n, gateway).statusCode())
        assertEquals(original, provider.pushes.last()["pushkey"].asString())
    }

    private fun savePush(n: Native, expected: Long? = null, enabled: Boolean = true) {
        val path = "/api/v1/auth/native/push-registration" + if (enabled) "" else "/disable"
        val body: Map<String, Any?> =
            if (enabled)
                mapOf(
                    "platform" to "ANDROID",
                    "token" to "fixture-fcm-${n.session}-${expected}",
                    "expectedVersion" to expected,
                )
            else mapOf("expectedVersion" to expected)
        val response =
            n.fixture.browser.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer ${n.access}")
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .method(
                        if (enabled) "PUT" else "POST",
                        HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)),
                    )
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, response.statusCode(), response.body())
    }

    private fun pusher(
        n: Native,
        token: String? = null,
        suffix: String = "",
        body: String? = null,
        duplicate: Boolean = false,
        method: String = "POST",
    ): HttpResponse<String> {
        val builder =
            HttpRequest.newBuilder(
                    URI(
                        "http://127.0.0.1:$port/_matrix/client/unstable/dev.fajar.hris/pusher$suffix"
                    )
                )
                .timeout(Duration.ofSeconds(10))
        token?.let { builder.header("Authorization", "Bearer $it") }
        if (duplicate) builder.header("Authorization", "Bearer ambiguous")
        return n.fixture.browser.send(
            builder
                .method(
                    method,
                    body?.let(HttpRequest.BodyPublishers::ofString)
                        ?: HttpRequest.BodyPublishers.noBody(),
                )
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    @Test
    fun groupIdentityHttpRecoversExpiredProviderAndKeepsSelfOperationsInTheGroupNamespace() {
        val n = native()
        val (device, token) = enrollGroup(n)
        val before = provider.queries.get()
        val reply = identity(n, token)
        assertEquals(200, reply.statusCode(), reply.body())
        val body = json.readTree(reply.body())
        assertEquals(setOf("user_id", "device_id", "is_guest"), body.propertyNames().toSet())
        val row =
            database()
                .queryForMap(
                    "select matrix_user_id,matrix_device_id from collaboration_space_devices where id=?",
                    device,
                )
        assertEquals(row["matrix_user_id"], body["user_id"].asString())
        assertEquals(row["matrix_device_id"], body["device_id"].asString())
        assertFalse(body["is_guest"].asBoolean())
        assertEquals(before + 1, provider.queries.get())
        val capabilities =
            n.fixture.browser.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/_matrix/client/v3/capabilities"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer $token")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, capabilities.statusCode(), capabilities.body())
        assertFalse(
            json
                .readTree(capabilities.body())["capabilities"]["m.change_password"]["enabled"]
                .asBoolean()
        )
        assertEquals(before + 1, provider.queries.get())
        assertEquals(400, identity(n, token, suffix = "?access_token=forbidden").statusCode())
        assertEquals(401, identity(n, token, duplicate = true).statusCode())
        assertEquals(
            400,
            identity(n, token, body = HttpRequest.BodyPublishers.ofString("{}")).statusCode(),
        )
        assertEquals(401, identity(n, n.access).statusCode())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_request_leases where device_id=?",
                    Int::class.java,
                    device,
                ),
        )
    }

    @Test
    fun groupIdentityHttpWithholdsAProviderReplyAfterItsNativeParentIsRevoked() {
        val n = native()
        val (device, token) = enrollGroup(n)
        val before = provider.queries.get()
        provider.onQuery.set {
            database()
                .update(
                    "update native_sessions set revoked_at=clock_timestamp(),version=version+1 where id=?",
                    n.session,
                )
        }
        val reply = identity(n, token)
        assertEquals(401, reply.statusCode(), reply.body())
        assertEquals("M_UNKNOWN_TOKEN", json.readTree(reply.body())["errcode"].asString())
        assertEquals(before + 1, provider.queries.get())
        assertEquals(401, identity(n, token).statusCode())
        assertEquals(before + 1, provider.queries.get())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_request_leases where device_id=?",
                    Int::class.java,
                    device,
                ),
        )
        assertEquals(
            1L,
            database()
                .queryForObject(
                    "select generation from collaboration_space_provider_sessions where device_id=?",
                    Long::class.java,
                    device,
                ),
        )
    }

    @Test
    fun groupKeyUploadUsesOriginalIdentityAndStrictGroupKeyDispatch() {
        val n = native()
        val (device, token) = enrollGroup(n)
        val row =
            database()
                .queryForMap(
                    "select matrix_user_id,matrix_device_id from collaboration_space_devices where id=?",
                    device,
                )
        val key = KeyPayload(row["matrix_user_id"] as String, row["matrix_device_id"] as String)
        val before = provider.keyUploads.get()
        assertEquals(401, uploadKeys(n, n.access).statusCode())
        assertEquals(401, uploadKeys(n, token, duplicate = true).statusCode())
        assertEquals(400, uploadKeys(n, token, suffix = "?access_token=forbidden").statusCode())
        assertEquals(400, uploadKeys(n, token, "{\"private_key\":\"forbidden\"}").statusCode())
        assertEquals(413, uploadKeys(n, token, " ".repeat(65537), chunked = true).statusCode())
        assertEquals(before, provider.keyUploads.get())
        assertEquals(200, uploadKeys(n, token).statusCode())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_key_publications where device_id=?",
                    Int::class.java,
                    device,
                ),
        )
        repeat(2) { index ->
            val result =
                uploadKeys(n, token, json.writeValueAsString(key.body()), chunked = index == 1)
            assertEquals(200, result.statusCode(), result.body())
            assertEquals("no-store", result.headers().firstValue("Cache-Control").orElse(""))
            assertEquals(
                setOf("one_time_key_counts"),
                json.readTree(result.body()).propertyNames().toSet(),
            )
        }
        assertEquals(
            1L,
            database()
                .queryForObject(
                    "select version from collaboration_space_key_publications where device_id=?",
                    Long::class.java,
                    device,
                ),
        )
        val replacement = KeyPayload(key.user, key.device)
        val count = provider.keyUploads.get()
        val denied = uploadKeys(n, token, json.writeValueAsString(replacement.body()))
        assertEquals(409, denied.statusCode(), denied.body())
        assertEquals(
            "collaboration_space_device_keys_changed",
            json.readTree(denied.body())["org.hris.error_code"].asString(),
        )
        assertEquals(count, provider.keyUploads.get())
        val query =
            uploadKeys(
                n,
                token,
                json.writeValueAsString(
                    mapOf("device_keys" to mapOf(key.user to emptyList<String>()))
                ),
                path = "query",
            )
        assertEquals(200, query.statusCode(), query.body())
        assertEquals(
            json.valueToTree(key.body()["device_keys"]),
            json.readTree(query.body())["device_keys"][key.user][key.device],
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_request_leases where device_id=?",
                    Int::class.java,
                    device,
                ),
        )
    }

    @Test
    fun groupUploadWithholdsReplyAfterNativeRevocationAndKeepsUncertainIdentity() {
        val n = native()
        val (device, token) = enrollGroup(n)
        val row =
            database()
                .queryForMap(
                    "select matrix_user_id,matrix_device_id from collaboration_space_devices where id=?",
                    device,
                )
        val key = KeyPayload(row["matrix_user_id"] as String, row["matrix_device_id"] as String)
        provider.onKeyUpload.set {
            database()
                .update(
                    "update native_sessions set revoked_at=clock_timestamp(),version=version+1 where id=?",
                    n.session,
                )
        }
        try {
            val reply = uploadKeys(n, token, json.writeValueAsString(key.body()))
            assertEquals(401, reply.statusCode(), reply.body())
            assertEquals("no-store", reply.headers().firstValue("Cache-Control").orElse(""))
            assertEquals(
                0L,
                database()
                    .queryForObject(
                        "select version from collaboration_space_key_publications where device_id=?",
                        Long::class.java,
                        device,
                    ),
            )
            val count = provider.keyUploads.get()
            assertEquals(
                401,
                uploadKeys(n, token, json.writeValueAsString(key.body())).statusCode(),
            )
            assertEquals(count, provider.keyUploads.get())
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from collaboration_space_request_leases where device_id=?",
                        Int::class.java,
                        device,
                    ),
            )
        } finally {
            provider.onKeyUpload.set(null)
        }
    }

    @Test
    fun groupKeyQueryAndClaimUseStrictOpaqueAuthorityAndOriginalSignedFields() {
        val n = native()
        val (device, token) = enrollGroup(n)
        val key = groupFixtureKeys(device)
        val queryBody =
            json.writeValueAsString(mapOf("device_keys" to mapOf(key.user to emptyList<String>())))
        val claimBody =
            json.writeValueAsString(
                mapOf(
                    "one_time_keys" to mapOf(key.user to mapOf(key.device to "signed_curve25519"))
                )
            )
        val beforeQueries = provider.keyQueries.get()
        val beforeClaims = provider.keyClaims.get()
        for (path in listOf("query", "claim")) {
            val body = if (path == "query") queryBody else claimBody
            // Invalid tokens enter company dispatch; isolate authentication with a shape valid
            // in both namespaces. Group device IDs remain intentionally invalid in company claims.
            val credentialBody =
                if (path == "claim")
                    body.replace(
                        key.device,
                        "HRIS_" + UUID.randomUUID().toString().replace("-", ""),
                    )
                else body
            for (credential in listOf(null, n.access, "fictional-provider-token")) assertEquals(
                401,
                uploadKeys(n, credential, credentialBody, path = path).statusCode(),
            )
            assertEquals(
                401,
                uploadKeys(n, token, body, duplicate = true, path = path).statusCode(),
            )
            assertEquals(400, uploadKeys(n, token, body + "{}", path = path).statusCode())
            assertEquals(
                400,
                uploadKeys(n, token, body, suffix = "?access_token=forbidden", path = path)
                    .statusCode(),
            )
            assertEquals(
                413,
                uploadKeys(n, token, " ".repeat(65537), chunked = true, path = path).statusCode(),
            )
        }
        assertEquals(400, uploadKeys(n, n.access, claimBody, path = "claim").statusCode())
        assertEquals(beforeQueries, provider.keyQueries.get())
        assertEquals(beforeClaims, provider.keyClaims.get())
        val pending = uploadKeys(n, token, claimBody, path = "claim")
        assertEquals(200, pending.statusCode(), pending.body())
        assertTrue(json.readTree(pending.body())["one_time_keys"][key.user].isEmpty)
        assertEquals(200, uploadKeys(n, token, json.writeValueAsString(key.body())).statusCode())
        val wrongAlias =
            "@hris_${UUID.randomUUID().toString().replace("-","")}_${n.fixture.account.toString().replace("-","")}:matrix.example.test"
        val mixed =
            json.writeValueAsString(
                mapOf(
                    "device_keys" to
                        mapOf(key.user to emptyList<String>(), wrongAlias to emptyList<String>())
                )
            )
        val queried = uploadKeys(n, token, mixed, chunked = true, path = "query")
        assertEquals(200, queried.statusCode(), queried.body())
        val data = json.readTree(queried.body())
        assertEquals(setOf("failures", "device_keys"), data.propertyNames().toSet())
        assertEquals(
            json.valueToTree(key.body()["device_keys"]),
            data["device_keys"][key.user][key.device],
        )
        assertTrue(data["device_keys"][wrongAlias].isEmpty)
        assertEquals("no-store", queried.headers().firstValue("Cache-Control").orElse(""))
        val claimed = uploadKeys(n, token, claimBody, chunked = true, path = "claim")
        assertEquals(200, claimed.statusCode(), claimed.body())
        assertEquals(
            json.valueToTree(key.body()["one_time_keys"]),
            json.readTree(claimed.body())["one_time_keys"][key.user][key.device],
        )
        assertEquals("no-store", claimed.headers().firstValue("Cache-Control").orElse(""))
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_request_leases where device_id=?",
                    Int::class.java,
                    device,
                ),
        )
    }

    @Test
    fun groupKeyDiscoveryReturnsRetryWhenPublicationChangesAndPendingCannotClaim() {
        val n = native()
        val (device, token) = enrollGroup(n)
        val key = groupFixtureKeys(device)
        val signed =
            matrixKeyUploadRequest(json.readTree(json.writeValueAsString(key.body()))).deviceKeys!!
        val fingerprint =
            (validateCollaborationDeviceKeys(signed, key.user, key.device) as Result.Success).value
        val scope = Actor(n.fixture.account, null, emptySet(), clock.instant(), UUID.randomUUID())
        val reservation =
            CollaborationSpaceKeyPublication(
                database()
                    .queryForObject(
                        "select space_id from collaboration_space_devices where id=?",
                        UUID::class.java,
                        device,
                    )!!,
                n.fixture.account,
                device,
                n.session,
                key.user,
                key.device,
                fingerprint,
                0,
                clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
            )
        assertInstanceOf(
            Result.Success::class.java,
            groupKeyTransactions.run(scope) { groupKeyPublications.reserve(reservation) },
        )
        provider.putDeviceKeys(key)
        val pending = queryFixtureKeys(n, token, key)
        assertEquals(200, pending.statusCode(), pending.body())
        assertEquals(
            json.valueToTree(key.body()["device_keys"]),
            json.readTree(pending.body())["device_keys"][key.user][key.device],
        )
        val count = provider.keyClaims.get()
        val claim =
            json.writeValueAsString(
                mapOf(
                    "one_time_keys" to mapOf(key.user to mapOf(key.device to "signed_curve25519"))
                )
            )
        val unconfirmed = uploadKeys(n, token, claim, path = "claim")
        assertEquals(200, unconfirmed.statusCode(), unconfirmed.body())
        assertTrue(json.readTree(unconfirmed.body())["one_time_keys"][key.user].isEmpty)
        assertEquals(count, provider.keyClaims.get())
        provider.onKeyQuery.set {
            assertInstanceOf(
                Result.Success::class.java,
                groupKeyTransactions.run(scope) {
                    groupKeyPublications.publish(
                        reservation,
                        clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
                    )
                },
            )
        }
        try {
            val changed = queryFixtureKeys(n, token, key)
            assertEquals(503, changed.statusCode(), changed.body())
            assertEquals(
                "collaboration_recipients_changed",
                json.readTree(changed.body())["org.hris.error_code"].asString(),
            )
            assertEquals("no-store", changed.headers().firstValue("Cache-Control").orElse(""))
            assertFalse(changed.body().contains(fingerprint))
            assertEquals(200, queryFixtureKeys(n, token, key).statusCode())
        } finally {
            provider.onKeyQuery.set(null)
        }
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_request_leases where device_id=?",
                    Int::class.java,
                    device,
                ),
        )
    }

    @Test
    fun groupQueryAndClaimWithholdSignedResultsAfterNativeRevocation() {
        for (path in listOf("query", "claim")) {
            val n = native()
            val (device, token) = enrollGroup(n)
            val key = groupFixtureKeys(device)
            assertEquals(
                200,
                uploadKeys(n, token, json.writeValueAsString(key.body())).statusCode(),
            )
            val hook = if (path == "query") provider.onKeyQuery else provider.onKeyClaim
            hook.set {
                database()
                    .update(
                        "update native_sessions set revoked_at=clock_timestamp(),version=version+1 where id=?",
                        n.session,
                    )
            }
            try {
                val body =
                    if (path == "query")
                        mapOf("device_keys" to mapOf(key.user to emptyList<String>()))
                    else
                        mapOf(
                            "one_time_keys" to
                                mapOf(key.user to mapOf(key.device to "signed_curve25519"))
                        )
                val response = uploadKeys(n, token, json.writeValueAsString(body), path = path)
                assertEquals(401, response.statusCode(), response.body())
                assertEquals(
                    "M_UNKNOWN_TOKEN",
                    json.readTree(response.body())["errcode"].asString(),
                )
                assertFalse(response.body().contains(key.user))
                assertFalse(response.body().contains(token))
                assertEquals(
                    1L,
                    database()
                        .queryForObject(
                            "select version from collaboration_space_key_publications where device_id=?",
                            Long::class.java,
                            device,
                        ),
                )
                assertEquals(
                    0,
                    database()
                        .queryForObject(
                            "select count(*) from collaboration_space_request_leases where device_id=?",
                            Int::class.java,
                            device,
                        ),
                )
            } finally {
                hook.set(null)
            }
        }
    }

    @Test
    fun groupEncryptedToDevicePreservesOriginalFramesAndStrictOpaqueHttpAuthority() {
        val n = native()
        val (device, token) = enrollGroup(n)
        val key = groupFixtureKeys(device)
        val body = encryptedBody(key)
        val before = provider.encryptedSends.get()
        fun send(
            value: String = body,
            auth: String? = token,
            duplicate: Boolean = false,
            suffix: String = "",
        ) =
            uploadKeys(
                n,
                auth,
                value,
                chunked = true,
                duplicate = duplicate,
                suffix = suffix,
                route = "sendToDevice/m.room.encrypted/fixture-txn",
                method = "PUT",
            )
        // Invalid credentials enter company dispatch, so isolate auth using a shared device shape.
        val credentialBody =
            body.replace(key.device, "HRIS_" + UUID.randomUUID().toString().replace("-", ""))
        for (auth in listOf(null, n.access, "fictional-provider-token")) assertEquals(
            401,
            send(credentialBody, auth).statusCode(),
        )
        assertEquals(400, send(auth = n.access).statusCode())
        assertEquals(401, send(duplicate = true).statusCode())
        assertEquals(400, send(suffix = "?access_token=forbidden").statusCode())
        for (invalid in
            listOf(
                "{}",
                "{\"messages\":{},\"messages\":{}}",
                body + "{}",
                body.replace("m.olm.v1.curve25519-aes-sha2", "plaintext"),
                body.replace("ZmljdGlvbmFs", "plaintext!"),
                body.replace(key.device, "*"),
            )) assertEquals(400, send(invalid).statusCode())
        assertEquals(413, send(" ".repeat(1_048_577)).statusCode())
        assertEquals(before, provider.encryptedSends.get())
        assertEquals(403, send().statusCode()) // No confirmed recipient publication yet.
        assertEquals(200, uploadKeys(n, token, json.writeValueAsString(key.body())).statusCode())
        val unknown =
            "@hrisg_${UUID.randomUUID().toString().replace("-", "")}_${n.fixture.account.toString().replace("-", "")}:matrix.example.test"
        assertEquals(400, send(body.replace(key.user, unknown)).statusCode()) // Foreign space.
        val stale =
            "@hris_${UUID.randomUUID().toString().replace("-", "")}_${n.fixture.account.toString().replace("-", "")}:matrix.example.test"
        assertEquals(403, send(body.replace(key.user, stale)).statusCode())
        assertEquals(before, provider.encryptedSends.get())
        repeat(2) {
            val response = send()
            assertEquals(200, response.statusCode(), response.body())
            assertEquals("{}", response.body())
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
            assertEquals(json.readTree(body), provider.lastEnvelope.get())
        }
        assertEquals(before + 2, provider.encryptedSends.get())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_request_leases where device_id=?",
                    Int::class.java,
                    device,
                ),
        )
    }

    @Test
    fun groupEncryptedToDeviceWithholdsResponsesAfterKeyReadOrSendRevocation() {
        for (duringSend in listOf(false, true)) {
            val n = native()
            val (device, token) = enrollGroup(n)
            val key = groupFixtureKeys(device)
            assertEquals(
                200,
                uploadKeys(n, token, json.writeValueAsString(key.body())).statusCode(),
            )
            val before = provider.encryptedSends.get()
            val hook = if (duringSend) provider.onEncryptedSend else provider.onKeyQuery
            hook.set {
                database()
                    .update(
                        "update native_sessions set revoked_at=clock_timestamp(),version=version+1 where id=?",
                        n.session,
                    )
            }
            try {
                val response =
                    uploadKeys(
                        n,
                        token,
                        encryptedBody(key),
                        route = "sendToDevice/m.room.encrypted/fixture-txn",
                        method = "PUT",
                    )
                assertEquals(401, response.statusCode(), response.body())
                assertEquals(
                    "M_UNKNOWN_TOKEN",
                    json.readTree(response.body())["errcode"].asString(),
                )
                assertFalse(response.body().contains(key.user))
                assertFalse(response.body().contains(token))
                assertEquals(before + if (duringSend) 1 else 0, provider.encryptedSends.get())
                assertEquals(
                    0,
                    database()
                        .queryForObject(
                            "select count(*) from collaboration_space_request_leases where device_id=?",
                            Int::class.java,
                            device,
                        ),
                )
            } finally {
                hook.set(null)
            }
        }
    }

    @Test
    fun groupRoomCiphertextUsesBoundCrossCompanyRoomAndStrictOpaqueAuthority() {
        val a = groupEmployee()
        val b = groupEmployee()
        val (device, token) = enrollGroup(a)
        val (peerDevice, peerToken) = enrollGroup(b)
        val key = groupFixtureKeys(device)
        val peerKey = groupFixtureKeys(peerDevice)
        assertEquals(200, uploadKeys(a, token, json.writeValueAsString(key.body())).statusCode())
        assertEquals(
            200,
            uploadKeys(b, peerToken, json.writeValueAsString(peerKey.body())).statusCode(),
        )
        val room = publishedGroupRoom(a, device, b)
        val body = groupRoomCiphertext(key)
        val before = provider.roomSends.get()
        fun send(
            value: String = body,
            auth: String? = token,
            suffix: String = "",
            duplicate: Boolean = false,
            target: String = room,
        ) =
            uploadKeys(
                a,
                auth,
                value,
                chunked = true,
                suffix = suffix,
                duplicate = duplicate,
                route = "rooms/$target/send/m.room.encrypted/fixture-txn",
                method = "PUT",
            )
        for (auth in listOf(null, a.access, "fictional-provider-token")) assertEquals(
            401,
            send(auth = auth).statusCode(),
        )
        assertEquals(401, send(duplicate = true).statusCode())
        assertEquals(400, send(suffix = "?access_token=forbidden").statusCode())
        for (invalid in
            listOf(
                "{}",
                body + "{}",
                body.replace("m.megolm.v1.aes-sha2", "plaintext"),
                body.replace("ZmljdGlvbmFs", "plaintext!"),
            )) assertEquals(400, send(invalid).statusCode())
        assertEquals(413, send(" ".repeat(65537)).statusCode())
        assertEquals(404, send(target = "!unrelated:matrix.example.test").statusCode())
        assertEquals(before, provider.roomSends.get())
        database()
            .update(
                "update native_sessions set access_expires_at=clock_timestamp()-interval '1 second' where id=?",
                b.session,
            )
        repeat(2) {
            val response = send()
            assertEquals(200, response.statusCode(), response.body())
            assertEquals("\$fixture-sent", json.readTree(response.body())["event_id"].asString())
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
            assertEquals(json.readTree(body), provider.lastRoomCiphertext.get())
        }
        assertEquals(before + 2, provider.roomSends.get())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_request_leases where device_id=?",
                    Int::class.java,
                    device,
                ),
        )
    }

    @Test
    fun groupRoomSendWithholdsProviderEventWhenReceiverIsRevokedAfterAcceptance() {
        val a = groupEmployee()
        val b = groupEmployee()
        val (device, token) = enrollGroup(a)
        val (peerDevice, peerToken) = enrollGroup(b)
        val key = groupFixtureKeys(device)
        val peerKey = groupFixtureKeys(peerDevice)
        assertEquals(200, uploadKeys(a, token, json.writeValueAsString(key.body())).statusCode())
        assertEquals(
            200,
            uploadKeys(b, peerToken, json.writeValueAsString(peerKey.body())).statusCode(),
        )
        val room = publishedGroupRoom(a, device, b)
        val before = provider.roomSends.get()
        provider.onRoomSend.set {
            database()
                .update(
                    "delete from membership_permissions where account_id=? and permission='collaboration.chat'",
                    b.fixture.account,
                )
        }
        try {
            val response =
                uploadKeys(
                    a,
                    token,
                    groupRoomCiphertext(key),
                    route = "rooms/$room/send/m.room.encrypted/fixture-txn",
                    method = "PUT",
                )
            assertEquals(503, response.statusCode(), response.body())
            assertFalse(response.body().contains("event_id"))
            assertFalse(response.body().contains(token))
            assertEquals(before + 1, provider.roomSends.get())
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from collaboration_space_request_leases where device_id=?",
                        Int::class.java,
                        device,
                    ),
            )
        } finally {
            provider.onRoomSend.set(null)
        }
    }

    @Test
    fun groupHistoryScopesOriginalEventsAndCursorsAndRetainsOffboardedPeerHistory() {
        val a = groupEmployee()
        val b = groupEmployee()
        val (device, token) = enrollGroup(a)
        val (peerDevice, peerToken) = enrollGroup(b)
        val room = publishedGroupRoom(a, device, b)
        val events = groupHistoryEvents(room, device, peerDevice)
        provider.roomStates[room] = events
        fun read(path: String, bearer: String? = token) =
            matrixRoom(a, bearer, "v3/rooms/$room/$path")
        val first = read("messages?dir=b&limit=10")
        assertEquals(200, first.statusCode(), first.body())
        assertEquals("no-store", first.headers().firstValue("Cache-Control").orElse(""))
        assertFalse(first.body().contains("private-history"))
        val body = json.readTree(first.body())
        assertEquals(3, body["chunk"].size())
        assertEquals(
            json.valueToTree<tools.jackson.databind.JsonNode>(events[0]["content"]),
            body["chunk"][0]["content"],
        )
        val cursor =
            java.net.URLEncoder.encode(
                body["end"].asString(),
                java.nio.charset.StandardCharsets.UTF_8,
            )
        val last = read("messages?dir=b&from=$cursor&limit=10")
        assertEquals(200, last.statusCode(), last.body())
        assertTrue(json.readTree(last.body())["chunk"].isEmpty)
        assertFalse(json.readTree(last.body()).has("end"))
        val event = events[0].getValue("event_id") as String
        val single = read("event/$event")
        assertEquals(200, single.statusCode(), single.body())
        assertEquals(event, json.readTree(single.body())["event_id"].asString())
        val context = read("context/$event?limit=0")
        assertEquals(200, context.statusCode(), context.body())
        assertEquals(event, json.readTree(context.body())["event"]["event_id"].asString())
        val before = provider.roomCalls.get()
        for (invalid in
            listOf(
                "messages",
                "messages?dir=b&limit=0",
                "messages?dir=f&limit=101",
                "messages?dir=b&limit=1&limit=2",
                "messages?dir=b&from=raw-position",
                "messages?dir=b&filter=%7B%22types%22%3A%5B%5D%7D",
                "context/$event?limit=101",
                "event/$event?access_token=forbidden",
            )) assertEquals(400, read(invalid).statusCode(), invalid)
        for (bearer in listOf(null, a.access, "fictional-provider-token")) assertEquals(
            401,
            read("messages?dir=b", bearer).statusCode(),
        )
        val duplicate =
            a.fixture.browser.send(
                HttpRequest.newBuilder(
                        URI("http://127.0.0.1:$port/_matrix/client/v3/rooms/$room/messages?dir=b")
                    )
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer $token")
                    .header("Authorization", "Bearer $token")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(401, duplicate.statusCode())
        assertEquals(
            400,
            matrixRoom(b, peerToken, "v3/rooms/$room/messages?dir=b&from=$cursor").statusCode(),
        )
        val outsider = groupEmployee()
        val (_, outsideToken) = enrollGroup(outsider)
        assertEquals(
            404,
            matrixRoom(outsider, outsideToken, "v3/rooms/$room/messages?dir=b").statusCode(),
        )
        assertEquals(before, provider.roomCalls.get())
        database()
            .update(
                "delete from membership_permissions where account_id=? and permission='collaboration.chat'",
                b.fixture.account,
            )
        val retained = read("messages?dir=b")
        assertEquals(200, retained.statusCode(), retained.body())
        assertEquals(event, json.readTree(retained.body())["chunk"][0]["event_id"].asString())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_space_request_leases where device_id=?",
                    Int::class.java,
                    device,
                ),
        )
    }

    @Test
    fun groupHistoryWithholdsProviderContentAfterCurrentNativeSessionRevocation() {
        val a = groupEmployee()
        val b = groupEmployee()
        val (device, token) = enrollGroup(a)
        val (peerDevice, _) = enrollGroup(b)
        val room = publishedGroupRoom(a, device, b)
        val events = groupHistoryEvents(room, device, peerDevice)
        provider.roomStates[room] = events
        val event = events[0].getValue("event_id") as String
        val before = provider.roomCalls.get()
        provider.onRoomHistory.set {
            database()
                .update(
                    "update native_sessions set revoked_at=clock_timestamp(),version=version+1 where id=?",
                    a.session,
                )
        }
        try {
            val response = matrixRoom(a, token, "v3/rooms/$room/context/$event?limit=0")
            assertEquals(401, response.statusCode(), response.body())
            assertFalse(response.body().contains(event))
            assertFalse(response.body().contains("ciphertext"))
            assertFalse(response.body().contains(token))
            assertEquals(before + 1, provider.roomCalls.get())
            assertEquals(
                0,
                database()
                    .queryForObject(
                        "select count(*) from collaboration_space_request_leases where device_id=?",
                        Int::class.java,
                        device,
                    ),
            )
        } finally {
            provider.onRoomHistory.set(null)
        }
    }

    private fun groupHistoryEvents(
        room: String,
        device: UUID,
        peerDevice: UUID,
    ): List<Map<String, Any>> {
        fun identity(id: UUID) =
            database()
                .queryForMap(
                    "select matrix_user_id,matrix_device_id from collaboration_space_devices where id=?",
                    id,
                )
        val own = identity(device)
        val peer = identity(peerDevice)
        val user = own.getValue("matrix_user_id") as String
        val other = peer.getValue("matrix_user_id") as String
        fun member(value: String, suffix: String): Map<String, Any> =
            mapOf(
                "type" to "m.room.member",
                "sender" to value,
                "state_key" to value,
                "event_id" to "\$group-$device-$suffix",
                "room_id" to room,
                "origin_server_ts" to 1,
                "content" to mapOf("membership" to "join"),
            )
        return listOf(
            mapOf(
                "type" to "m.room.encrypted",
                "sender" to user,
                "room_id" to room,
                "event_id" to "\$group-history-$device",
                "origin_server_ts" to 2,
                "content" to
                    mapOf(
                        "algorithm" to "m.megolm.v1.aes-sha2",
                        "ciphertext" to "ZmljdGlvbmFs",
                        "session_id" to
                            Base64.getEncoder()
                                .withoutPadding()
                                .encodeToString(ByteArray(32) { 7 }),
                        "device_id" to own.getValue("matrix_device_id"),
                    ),
            ),
            member(user, "own"),
            member(other, "peer"),
        )
    }

    private fun groupEmployee(): Native {
        val admin = fixture()
        val account = UUID.randomUUID()
        val email = "$account@example.test"
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Room HTTP employee',password_hash from accounts where email='admin@example.test'",
                account,
                email,
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                admin.company,
                account,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                admin.company,
                account,
            )
        val browser = client()
        val csrf = login(browser, email)
        val exchanged =
            command(
                browser,
                "/api/v1/auth/native/exchange",
                """{"deviceName":"Room HTTP employee fixture"}""",
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, exchanged.statusCode(), exchanged.body())
        val data = json.readTree(exchanged.body())
        return Native(
            Fixture(
                browser,
                csrf,
                admin.company,
                account,
                Actor(
                    account,
                    admin.company,
                    setOf("collaboration.chat"),
                    clock.instant(),
                    UUID.randomUUID(),
                    credentialVersion = 0,
                ),
            ),
            UUID.fromString(data["sessionId"].asString()),
            data["accessToken"].asString(),
        )
    }

    private fun groupRoomCiphertext(key: KeyPayload): String {
        val curve =
            json
                .valueToTree<tools.jackson.databind.JsonNode>(key.body())["device_keys"]["keys"][
                    "curve25519:${key.device}"]
                .asString()
        return json.writeValueAsString(
            mapOf(
                "algorithm" to "m.megolm.v1.aes-sha2",
                "ciphertext" to "ZmljdGlvbmFs",
                "session_id" to
                    Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { 7 }),
                "sender_key" to curve,
                "device_id" to key.device,
            )
        )
    }

    private fun publishedGroupRoom(a: Native, device: UUID, peer: Native): String {
        assertNotEquals(a.fixture.account, peer.fixture.account)
        assertNotEquals(a.fixture.company, peer.fixture.company)
        val db = database()
        val space =
            requireNotNull(
                db.queryForObject("select id from collaboration_spaces", UUID::class.java)
            )
        val base = "/api/v1/collaboration/spaces/$space"
        val opened =
            groupNativeCall(
                a,
                "$base/direct-conversations",
                mapOf("peerAccountId" to peer.fixture.account.toString()),
            )
        assertEquals(200, opened.statusCode(), opened.body())
        val conversation = UUID.fromString(json.readTree(opened.body())["id"].asString())
        val version =
            requireNotNull(
                db.queryForObject(
                    "select version from collaboration_space_devices where id=?",
                    Long::class.java,
                    device,
                )
            )
        val requested =
            groupNativeCall(
                a,
                "$base/direct-conversations/$conversation/room",
                mapOf("deviceId" to device.toString(), "expectedDeviceVersion" to version),
            )
        assertEquals(200, requested.statusCode(), requested.body())

        // Owned worker storage fixture. Provider reconciliation has separate actual Synapse tests;
        // the HTTP request itself runs through the ordinary application role and current admission.
        val source = requireNotNull(db.dataSource)
        val transactions =
            dev.fajar.hris.core.database.PostgresTransactionRunner(
                org.springframework.jdbc.support.JdbcTransactionManager(source),
                db,
            )
        val repository =
            dev.fajar.hris.collaboration.data.repositories.StoredCollaborationSpaceRoomRepository(
                dev.fajar.hris.collaboration.data.datasources
                    .PostgresCollaborationSpaceRoomDataSource(
                        org.jooq.impl.DSL.using(
                            org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(
                                source
                            ),
                            org.jooq.SQLDialect.POSTGRES,
                        )
                    )
            )
        val claim =
            transactions.run(
                Actor(UUID(0, 0), null, emptySet(), java.time.Instant.EPOCH, UUID.randomUUID())
            ) {
                repository.claim(UUID.randomUUID(), 8, 60)
            }
        assertInstanceOf(Result.Success::class.java, claim)
        val leases = (claim as Result.Success).value
        assertEquals(1, leases.size)
        val lease = requireNotNull(leases.single().lease)
        assertEquals(conversation, lease.request.conversationId)
        val room = "!group-fixture-${UUID.randomUUID()}:matrix.example.test"
        val actor =
            Actor(
                a.fixture.account,
                null,
                emptySet(),
                lease.request.authenticatedAt,
                UUID.randomUUID(),
                mfaVerifiedAt = lease.request.mfaVerifiedAt,
                credentialVersion = lease.request.credentialVersion,
            )
        val published =
            transactions.run(actor) {
                repository
                    .publish(
                        dev.fajar.hris.collaboration.domain.entities.CollaborationSpaceRoomBinding(
                            space,
                            conversation,
                            a.fixture.account,
                            lease.request.revision,
                            lease.token,
                            room,
                            clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
                            a.fixture.account,
                        )
                    )
                    .flatMap { repository.finish(lease).map { assertTrue(it) } }
            }
        assertEquals(Result.Success(Unit), published)
        provider.roomStates[room] = emptyList()
        return room
    }

    private fun groupFixtureKeys(device: UUID): KeyPayload {
        val row =
            database()
                .queryForMap(
                    "select matrix_user_id,matrix_device_id from collaboration_space_devices where id=?",
                    device,
                )
        return KeyPayload(row["matrix_user_id"] as String, row["matrix_device_id"] as String)
    }

    private fun groupNativeCall(
        n: Native,
        path: String,
        body: Map<String, Any>? = null,
    ): HttpResponse<String> {
        val builder =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer ${n.access}")
                .header("X-HRIS-Client-Platform", "ANDROID")
                .header("X-HRIS-Client-Build", "1")
        if (body == null) builder.GET()
        else
            builder
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
        return n.fixture.browser.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun enrollGroup(n: Native): Pair<UUID, String> {
        ownedParents.add(n.session)
        val current = groupNativeCall(n, "/api/v1/collaboration/spaces/current")
        assertEquals(200, current.statusCode(), current.body())
        val space = json.readTree(current.body())["spaceId"].asString()
        val base = "/api/v1/collaboration/spaces/$space"
        assertEquals(200, groupNativeCall(n, "$base/identity", emptyMap()).statusCode())
        val registered =
            groupNativeCall(
                n,
                "$base/native-device",
                mapOf(
                    "platform" to "ANDROID",
                    "origin" to "SPACE",
                    "label" to "Group protocol fixture",
                ),
            )
        assertEquals(200, registered.statusCode(), registered.body())
        val device = UUID.fromString(json.readTree(registered.body())["id"].asString())
        val db = database()
        val at = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)
        val expires = at.plusSeconds(1)
        db.update(
            "update collaboration_space_provisioning set state='RUNNING',attempts=1,lease_owner=?,lease_token=?,lease_until=least(expires_at,clock_timestamp()+interval '30 seconds') where device_id=?",
            UUID.randomUUID(),
            UUID.randomUUID(),
            device,
        )
        db.update(
            "insert into collaboration_space_provider_sessions(space_id,device_id,account_id,native_session_id,matrix_user_id,matrix_device_id,generation,token_encrypted,created_at,updated_at,token_expires_at) select space_id,id,account_id,origin_native_session_id,matrix_user_id,matrix_device_id,0,'expired-fixture-envelope',?::timestamptz,?::timestamptz,?::timestamptz from collaboration_space_devices where id=?",
            at.toString(),
            at.toString(),
            expires.toString(),
            device,
        )
        db.update(
            "update collaboration_space_devices set state='ACTIVE',version=version+1,updated_at=?::timestamptz where id=?",
            at.toString(),
            device,
        )
        db.update(
            "update collaboration_space_provisioning set state='SUCCEEDED',lease_owner=null,lease_token=null,lease_until=null,completed_at=?::timestamptz,last_outcome_revision=revision,last_outcome_state='SUCCEEDED',last_outcome_at=?::timestamptz where device_id=?",
            at.toString(),
            at.toString(),
            device,
        )
        val remaining = Duration.between(java.time.Instant.now(), expires).toNanos()
        if (remaining > 0) TimeUnit.NANOSECONDS.sleep(remaining + 20_000_000)
        val input = mapOf("deviceId" to device, "expectedDeviceVersion" to 1)
        val renewed = groupNativeCall(n, "$base/native-device/provider/renew", input)
        assertEquals(200, renewed.statusCode(), renewed.body())
        assertEquals(1L, json.readTree(renewed.body())["providerGeneration"].asLong())
        val bootstrap = groupNativeCall(n, "$base/native-device/bootstrap", input)
        assertEquals(200, bootstrap.statusCode(), bootstrap.body())
        return device to json.readTree(bootstrap.body())["accessToken"].asString()
    }

    private fun enroll(n: Native): Pair<UUID, String> {
        ownedParents.add(n.session)
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

    private fun publishedRoom(n: Native, device: UUID, peer: UUID = UUID.randomUUID()): String {
        // Owned SQL/protocol fixture; the actual publication worker has separate coverage.
        val db = database()
        val conversation = UUID.randomUUID()
        val job = UUID.randomUUID()
        val at = java.sql.Timestamp.from(clock.instant())
        db.update(
            "insert into accounts(id,email,display_name) values(?,?,'Fictional room peer')",
            peer,
            "$peer@example.test",
        )
        val pair = listOf(n.fixture.account, peer).sortedBy(UUID::toString)
        db.update(
            "insert into collaboration_direct_conversations(company_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,?)",
            n.fixture.company,
            conversation,
            pair[0],
            pair[1],
            n.fixture.account,
            at,
        )
        db.update(
            """insert into background_jobs(id,company_id,actor_id,kind,operation_id,request,authenticated_at,credential_version,correlation_id,created_at,total_items) values(?,?,?,'COLLABORATION_DIRECT_PROVISION',?,?::jsonb,?,0,?,?,1)""",
            job,
            n.fixture.company,
            n.fixture.account,
            UUID.randomUUID(),
            json.writeValueAsString(
                mapOf(
                    "conversationId" to conversation,
                    "deviceId" to device,
                    "nativeSessionId" to n.session,
                    "platform" to "ANDROID",
                    "clientBuild" to "1",
                )
            ),
            at,
            UUID.randomUUID(),
            at,
        )
        db.update(
            "insert into collaboration_direct_room_provisioning(company_id,conversation_id,account_id,device_id,job_id,version,created_at,updated_at) values(?,?,?,?,?,0,?,?)",
            n.fixture.company,
            conversation,
            n.fixture.account,
            device,
            job,
            at,
            at,
        )
        db.update(
            "update background_jobs set status='RUNNING',attempts=1,lease_owner=gen_random_uuid(),lease_token=gen_random_uuid(),lease_until=now()+interval '1 minute' where id=?",
            job,
        )
        val room = "!fixture-${UUID.randomUUID()}"
        db.update(
            "insert into collaboration_direct_room_bindings(company_id,conversation_id,account_id,job_id,provisioning_version,matrix_server_name,matrix_room_id,created_at) values(?,?,?,?,0,'matrix.example.test',?,?)",
            n.fixture.company,
            conversation,
            n.fixture.account,
            job,
            room,
            at,
        )
        db.update(
            "update background_jobs set status='SUCCEEDED',completed_items=1,lease_owner=null,lease_token=null,lease_until=null,finished_at=now() where id=?",
            job,
        )
        fun user(account: UUID) =
            "@hris_${n.fixture.company.toString().replace("-", "")}_${account.toString().replace("-", "")}:matrix.example.test"
        fun event(type: String, key: String, content: Map<String, Any>) =
            mapOf(
                "event_id" to "\$${UUID.randomUUID()}",
                "room_id" to room,
                "type" to type,
                "state_key" to key,
                "sender" to user(n.fixture.account),
                "origin_server_ts" to 1,
                "content" to content,
            )
        provider.roomStates[room] =
            listOf(
                event("m.room.create", "", mapOf("room_version" to "12", "m.federate" to false)),
                event("m.room.encryption", "", mapOf("algorithm" to "m.megolm.v1.aes-sha2")),
                event("m.room.member", user(n.fixture.account), mapOf("membership" to "join")),
                event("m.room.member", user(peer), mapOf("membership" to "invite")),
            )
        return room
    }

    private fun matrixRoom(
        n: Native,
        token: String?,
        route: String,
        body: String? = null,
    ): HttpResponse<String> {
        val request =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/_matrix/client/$route"))
                .timeout(Duration.ofSeconds(15))
        token?.let { request.header("Authorization", "Bearer $it") }
        if (body == null) request.GET()
        else
            request
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
        return n.fixture.browser.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun directMetadata(
        n: Native,
        token: String?,
        user: String,
        body: String? = null,
        suffix: String = "",
    ): HttpResponse<String> {
        val encoded = java.net.URLEncoder.encode(user, java.nio.charset.StandardCharsets.UTF_8)
        val request =
            HttpRequest.newBuilder(
                    URI(
                        "http://127.0.0.1:$port/_matrix/client/v3/user/$encoded/account_data/m.direct$suffix"
                    )
                )
                .timeout(Duration.ofSeconds(15))
        token?.let { request.header("Authorization", "Bearer $it") }
        if (body == null) request.GET()
        else
            request
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
        return n.fixture.browser.send(request.build(), HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun directMetadataReadsAndConfirmsOnlyTheCurrentHrisOwnedMap() {
        val n = native()
        val (device, token) = enroll(n)
        val peer = UUID.randomUUID()
        val room = publishedRoom(n, device, peer)
        fun user(account: UUID) =
            "@hris_${n.fixture.company.toString().replace("-", "")}_${account.toString().replace("-", "")}:matrix.example.test"
        val expected = json.writeValueAsString(mapOf(user(peer) to listOf(room)))
        val current = directMetadata(n, token, user(n.fixture.account))
        assertEquals(200, current.statusCode(), current.body())
        assertEquals(json.readTree(expected), json.readTree(current.body()))
        assertEquals("no-store", current.headers().firstValue("Cache-Control").orElse(""))
        repeat(2) {
            val confirmed = directMetadata(n, token, user(n.fixture.account), expected)
            assertEquals(200, confirmed.statusCode(), confirmed.body())
            assertTrue(json.readTree(confirmed.body()).isEmpty)
        }
        val changed = directMetadata(n, token, user(n.fixture.account), "{}")
        assertEquals(409, changed.statusCode(), changed.body())
        assertEquals(
            "collaboration_direct_metadata_managed",
            json.readTree(changed.body()).path("org.hris.error_code").asString(),
        )
        assertEquals(403, directMetadata(n, token, user(peer)).statusCode())
        val other = native()
        val (_, otherToken) = enroll(other)
        assertEquals(403, directMetadata(other, otherToken, user(n.fixture.account)).statusCode())
        assertEquals(
            json.readTree(expected),
            json.readTree(directMetadata(n, token, user(n.fixture.account)).body()),
        )
        database()
            .update(
                "update native_sessions set revoked_at=now(),version=version+1 where id=?",
                n.session,
            )
        assertEquals(401, directMetadata(n, token, user(n.fixture.account), expected).statusCode())
    }

    @Test
    fun directMetadataRejectsMalformedAndUnauthenticatedRequests() {
        val n = native()
        val (_, token) = enroll(n)
        val user =
            "@hris_${n.fixture.company.toString().replace("-", "")}_${n.fixture.account.toString().replace("-", "")}:matrix.example.test"
        assertEquals(200, directMetadata(n, token, user, "{}").statusCode())
        for (body in
            listOf(
                "[]",
                "{\"@peer\":null}",
                "{\"@peer\":[1]}",
                "{\"@peer\":[\"!a\",\"!a\"]}",
                "{\"@peer\":[\"!a\"],\"@peer\":[\"!b\"]}",
                "{} {}",
            )) assertEquals(400, directMetadata(n, token, user, body).statusCode())
        assertEquals(413, directMetadata(n, token, user, " ".repeat(1_048_577)).statusCode())
        assertEquals(401, directMetadata(n, null, user).statusCode())
        assertEquals(401, directMetadata(n, null, user, "{}").statusCode())
        assertEquals(401, directMetadata(n, n.access, user, "{}").statusCode())
        assertEquals(400, directMetadata(n, token, user, suffix = "?extra=1").statusCode())
        assertEquals(400, directMetadata(n, token, user, "{}", "?extra=1").statusCode())
    }

    @Test
    fun roomReadsJoinAndDiscoveryUseCurrentNativeOwnershipAndBoundMemberPosition() {
        val n = native()
        val (device, token) = enroll(n)
        val room = publishedRoom(n, device)
        val versions = matrixRoom(n, null, "versions")
        assertEquals(200, versions.statusCode(), versions.body())
        assertTrue(json.readTree(versions.body()).path("unstable_features").isEmpty)
        val capabilities = matrixRoom(n, token, "v3/capabilities")
        assertEquals(200, capabilities.statusCode(), capabilities.body())
        assertEquals(
            "12",
            json
                .readTree(capabilities.body())
                .path("capabilities")
                .path("m.room_versions")
                .path("default")
                .asString(),
        )
        for (route in listOf("v3/rooms/$room/join", "v3/join/$room")) {
            val joined = matrixRoom(n, token, route, "{}")
            assertEquals(200, joined.statusCode(), joined.body())
            assertEquals(room, json.readTree(joined.body()).path("room_id").asString())
        }
        val state = matrixRoom(n, token, "v3/rooms/$room/state")
        assertEquals(200, state.statusCode(), state.body())
        assertEquals(4, json.readTree(state.body()).size())
        val encryption = matrixRoom(n, token, "v3/rooms/$room/state/m.room.encryption")
        assertEquals(200, encryption.statusCode(), encryption.body())
        assertEquals(
            "m.megolm.v1.aes-sha2",
            json.readTree(encryption.body()).path("algorithm").asString(),
        )
        val synced = sync(n, token)
        assertEquals(200, synced.statusCode(), synced.body())
        val cursor =
            java.net.URLEncoder.encode(
                json.readTree(synced.body()).path("next_batch").asString(),
                java.nio.charset.StandardCharsets.UTF_8,
            )
        val members = matrixRoom(n, token, "v3/rooms/$room/members?at=$cursor")
        assertEquals(200, members.statusCode(), members.body())
        assertEquals(2, json.readTree(members.body()).path("chunk").size())
        assertEquals("at=fixture-private-since", provider.lastMemberQuery.get())
        assertEquals("no-store", members.headers().firstValue("Cache-Control").orElse(""))
        val before = provider.roomCalls.get()
        assertEquals(401, matrixRoom(n, n.access, "v3/rooms/$room/state").statusCode())
        assertEquals(401, matrixRoom(n, null, "v3/capabilities").statusCode())
        assertEquals(404, matrixRoom(n, token, "v3/rooms/!unknown/state").statusCode())
        assertEquals(
            400,
            matrixRoom(n, token, "v3/rooms/$room/join", "{\"third_party_signed\":{}}").statusCode(),
        )
        assertEquals(400, matrixRoom(n, token, "v3/rooms/$room/members?at=a&at=b").statusCode())
        val other = native()
        val (_, otherToken) = enroll(other)
        assertEquals(404, matrixRoom(other, otherToken, "v3/rooms/$room/state").statusCode())
        assertEquals(before, provider.roomCalls.get())
    }

    @Test
    fun readReceiptsUseAnExactEmptyBodyAndCurrentOwnedRoomAndEvent() {
        val n = native()
        val (device, token) = enroll(n)
        val room = publishedRoom(n, device)
        val event = provider.roomStates.getValue(room).first().getValue("event_id") as String
        fun route(type: String = "m.read.private", id: String = room) =
            "v3/rooms/$id/receipt/$type/$event"
        val initial = provider.roomReceipts.get()
        for (type in listOf("m.read", "m.read.private")) {
            repeat(2) {
                val response = matrixRoom(n, token, route(type), "{}")
                assertEquals(200, response.statusCode(), response.body())
                assertEquals("{}", response.body())
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
            }
        }
        assertEquals(initial + 4, provider.roomReceipts.get())
        for (body in
            listOf(
                "[]",
                "null",
                "{} {}",
                "{\"ts\":1}",
                "{\"thread_id\":\"main\"}",
                "{\"ts\":1,\"ts\":2}",
            )) {
            assertEquals(400, matrixRoom(n, token, route(), body).statusCode())
        }
        assertEquals(413, matrixRoom(n, token, route(), " ".repeat(257)).statusCode())
        assertEquals(400, matrixRoom(n, token, route() + "?extra=1", "{}").statusCode())
        assertEquals(403, matrixRoom(n, token, route("m.fully_read"), "{}").statusCode())
        assertEquals(401, matrixRoom(n, null, route(), "{}").statusCode())
        assertEquals(401, matrixRoom(n, n.access, route(), "{}").statusCode())
        assertEquals(404, matrixRoom(n, token, route(id = "!foreign"), "{}").statusCode())
        val other = native()
        val (_, otherToken) = enroll(other)
        assertEquals(404, matrixRoom(other, otherToken, route(), "{}").statusCode())
        assertEquals(initial + 4, provider.roomReceipts.get())
    }

    @Test
    fun historyRoutesScopePositionsValidateQueriesAndWithholdLateResults() {
        val n = native()
        val (device, token) = enroll(n)
        val room = publishedRoom(n, device)
        fun read(path: String, bearer: String = token) =
            matrixRoom(n, bearer, "v3/rooms/$room/$path")
        val page = read("messages?dir=b&limit=10")
        assertEquals(200, page.statusCode(), page.body())
        assertEquals("no-store", page.headers().firstValue("Cache-Control").orElse(""))
        val body = json.readTree(page.body())
        assertFalse(page.body().contains("private-history"))
        val cursor =
            java.net.URLEncoder.encode(
                body.path("end").asString(),
                java.nio.charset.StandardCharsets.UTF_8,
            )
        assertEquals(200, read("messages?dir=b&from=$cursor&limit=10").statusCode())
        assertTrue(provider.lastHistoryQuery.get().contains("from=private-history-1"))
        val eventId = body.path("chunk").first().path("event_id").asString()
        assertEquals(200, read("event/$eventId").statusCode())
        val context = read("context/$eventId?limit=0")
        assertEquals(200, context.statusCode(), context.body())
        val before = provider.roomCalls.get()
        for (invalid in
            listOf(
                "messages",
                "messages?dir=b&limit=0",
                "messages?dir=b&limit=101",
                "messages?dir=b&limit=+1",
                "messages?dir=b&limit=1&limit=2",
                "messages?dir=b&filter=%7B%22types%22%3A%5B%5D%7D",
                "messages?dir=b&from=raw-position",
                "context/$eventId?limit=101",
                "event/$eventId?access_token=foreign",
            )) {
            assertEquals(400, read(invalid).statusCode(), invalid)
        }
        assertEquals(401, read("messages?dir=b", n.access).statusCode())
        val other = native()
        val (_, otherToken) = enroll(other)
        assertEquals(
            404,
            matrixRoom(other, otherToken, "v3/rooms/$room/messages?dir=b&from=$cursor").statusCode(),
        )
        assertEquals(before, provider.roomCalls.get())
        provider.onRoomHistory.set {
            database()
                .update(
                    "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                    n.fixture.company,
                    n.fixture.account,
                )
        }
        val denied = read("messages?dir=b")
        assertTrue(denied.statusCode() in listOf(401, 403, 409), denied.body())
        assertFalse(denied.body().contains(eventId))
        assertEquals(before + 1, provider.roomCalls.get())
    }

    @Test
    fun historyMembershipFiltersReachProviderAndRejectMalformedOrEventFilteringInput() {
        val n = native()
        val (device, token) = enroll(n)
        val room = publishedRoom(n, device)
        fun encoded(value: String) =
            java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
        fun read(path: String) = matrixRoom(n, token, "v3/rooms/$room/$path")
        val filter = """{"lazy_load_members":true,"include_redundant_members":true}"""
        val page = read("messages?dir=b&limit=10&filter=${encoded(filter)}")
        assertEquals(200, page.statusCode(), page.body())
        assertEquals(
            json.readTree(filter),
            json.readTree(provider.lastHistoryQuery.get().substringAfter("filter=")),
        )
        val event = json.readTree(page.body()).path("chunk").first().path("event_id").asString()
        val context = read("context/$event?limit=20&filter=${encoded(filter)}")
        assertEquals(200, context.statusCode(), context.body())
        assertEquals(
            json.readTree(filter),
            json.readTree(provider.lastContextQuery.get().substringAfter("filter=")),
        )
        assertEquals(200, read("context/$event?filter=${encoded("{}")}").statusCode())
        assertEquals(200, read("messages?dir=b&filter=${encoded("{}")}").statusCode())
        val before = provider.roomCalls.get()
        for (invalid in
            listOf(
                "",
                "  ",
                "null",
                "[]",
                "true",
                "{",
                "{} {}",
                " ".repeat(257),
                """{"types":["m.room.message"]}""",
                """{"senders":[]}""",
                """{"lazy_load_members":1}""",
                """{"lazy_load_members":"true"}""",
                """{"include_redundant_members":true}""",
                """{"lazy_load_members":true,"lazy_load_members":false}""",
                """{"lazy_load_members":{"nested":{"deep":true}}}""",
            )) {
            assertEquals(400, read("context/$event?filter=${encoded(invalid)}").statusCode())
            assertEquals(400, read("messages?dir=b&filter=${encoded(invalid)}").statusCode())
        }
        assertEquals(
            400,
            read("context/$event?filter=${encoded(filter)}&filter=${encoded(filter)}").statusCode(),
        )
        assertEquals(before, provider.roomCalls.get())
    }

    private fun sync(
        n: Native,
        token: String? = null,
        suffix: String = "",
        duplicate: Boolean = false,
        body: String? = null,
    ): HttpResponse<String> {
        val builder =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/_matrix/client/v3/sync$suffix"))
                .timeout(Duration.ofSeconds(15))
        token?.let { builder.header("Authorization", "Bearer $it") }
        if (duplicate) builder.header("Authorization", "Bearer duplicate")
        if (body == null) builder.GET()
        else builder.method("GET", HttpRequest.BodyPublishers.ofString(body))
        return n.fixture.browser.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun syncReturnsOnlyEncryptedPositionAndWithholdsProviderSuccessAfterRevocation() {
        val n = native()
        val (_, token) = enroll(n)
        val response = sync(n, token, "?timeout=0&use_state_after=true")
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
        val cursor = json.readTree(response.body()).path("next_batch").asString()
        assertEquals(200, sync(n, token, "?use_state_after=false").statusCode())
        assertFalse(cursor.contains("fixture-private-since"))
        val repeated =
            sync(
                n,
                token,
                "?since=" +
                    java.net.URLEncoder.encode(cursor, java.nio.charset.StandardCharsets.UTF_8),
            )
        assertEquals(200, repeated.statusCode(), repeated.body())
        provider.onSync.set {
            database()
                .update(
                    "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                    n.fixture.company,
                    n.fixture.account,
                )
        }
        val denied = sync(n, token)
        assertEquals(403, denied.statusCode(), denied.body())
        assertFalse(denied.body().contains("next_batch"))
    }

    @Test
    fun syncRejectsAmbiguousQueryCookieNativeAndBodyCredentialsBeforeProviderAccess() {
        val n = native()
        val (_, token) = enroll(n)
        val calls = provider.syncs.get()
        for (result in
            listOf(sync(n), sync(n, n.access), sync(n, token, duplicate = true))) assertEquals(
            401,
            result.statusCode(),
            result.body(),
        )
        for (suffix in
            listOf(
                "?access_token=fixture",
                "?since=a&since=b",
                "?timeout=-1",
                "?timeout=30001",
                "?full_state=yes",
                "?filter=foreign-filter",
                "?use_state_after=1",
                "?use_state_after=true&use_state_after=false",
                "?set_presence=unknown",
            )) {
            val result = sync(n, token, suffix)
            assertEquals(400, result.statusCode(), result.body())
        }
        assertEquals(400, sync(n, token, body = "{}").statusCode())
        assertEquals(calls, provider.syncs.get())
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

    private fun uploadKeys(
        n: Native,
        token: String?,
        body: String = "{}",
        suffix: String = "",
        duplicate: Boolean = false,
        contentType: String = "application/json",
        chunked: Boolean = false,
        path: String = "upload",
        route: String = "keys/$path",
        method: String = "POST",
    ): HttpResponse<String> {
        val builder =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/_matrix/client/v3/$route$suffix"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", contentType)
        token?.let { builder.header("Authorization", "Bearer $it") }
        if (duplicate) builder.header("Authorization", "Bearer fictional-duplicate")
        val bytes = body.toByteArray()
        val publisher =
            if (chunked)
                HttpRequest.BodyPublishers.ofInputStream { java.io.ByteArrayInputStream(bytes) }
            else HttpRequest.BodyPublishers.ofByteArray(bytes)
        return n.fixture.browser.send(
            builder.method(method, publisher).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun fixtureKeys(n: Native, device: UUID) =
        KeyPayload(
            "@hris_${n.fixture.company.toString().replace("-", "")}_${n.fixture.account.toString().replace("-", "")}:matrix.example.test",
            "HRIS_${device.toString().replace("-", "")}",
        )

    private fun encryptedBody(key: KeyPayload): String {
        val own =
            json
                .valueToTree<tools.jackson.databind.JsonNode>(key.body())
                .path("device_keys")
                .path("keys")
                .path("curve25519:${key.device}")
                .asString()
        return json.writeValueAsString(
            mapOf(
                "messages" to
                    mapOf(
                        key.user to
                            mapOf(
                                key.device to
                                    mapOf(
                                        "algorithm" to "m.olm.v1.curve25519-aes-sha2",
                                        "sender_key" to own,
                                        "ciphertext" to
                                            mapOf(
                                                own to mapOf("type" to 0, "body" to "ZmljdGlvbmFs")
                                            ),
                                        "org.matrix.msgid" to "fixture-olm-message",
                                    )
                            )
                    )
            )
        )
    }

    private fun enrolledRoomPeer(n: Native, peer: UUID, published: Boolean = true): UUID {
        val db = database()
        val parent = UUID.randomUUID()
        val device = UUID.randomUUID()
        ownedParents.add(parent)
        db.update(
            "insert into company_memberships(company_id,account_id) values(?,?)",
            n.fixture.company,
            peer,
        )
        db.update(
            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
            n.fixture.company,
            peer,
        )
        db.update(
            "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,access_expires_at,credential_version,created_at) values(?,?,?,now()-interval '1 minute',now()+interval '1 day',now()-interval '1 hour',0,now()-interval '1 minute')",
            parent,
            peer,
            parent.toString().replace("-", "").repeat(2),
        )
        db.update(
            "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional offline peer','PROVISIONING',0,now()-interval '1 minute',now()-interval '1 minute')",
            n.fixture.company,
            device,
            peer,
        )
        db.update(
            "update collaboration_devices set state='ACTIVE',version=1 where company_id=? and id=?",
            n.fixture.company,
            device,
        )
        db.update(
            "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,now())",
            n.fixture.company,
            device,
            peer,
            parent,
        )
        db.update(
            "insert into collaboration_device_key_publications(company_id,device_id,account_id,fingerprint,version,reserved_at) values(?,?,?,?,0,now()-interval '1 minute')",
            n.fixture.company,
            device,
            peer,
            "a".repeat(64),
        )
        if (published)
            db.update(
                "update collaboration_device_key_publications set version=1,published_at=now() where company_id=? and device_id=?",
                n.fixture.company,
                device,
            )
        return parent
    }

    @Test
    fun encryptedRoomSendUsesCurrentPeerAndPinnedSenderAndStrictCiphertextFraming() {
        val n = native()
        val (device, token) = enroll(n)
        val key = fixtureKeys(n, device)
        assertEquals(200, uploadKeys(n, token, json.writeValueAsString(key.body())).statusCode())
        val peer = UUID.randomUUID()
        val room = publishedRoom(n, device, peer)
        val curve =
            json
                .readTree(json.writeValueAsString(key.body()))
                .path("device_keys")
                .path("keys")
                .path("curve25519:${key.device}")
                .asString()
        val content =
            mapOf(
                "algorithm" to "m.megolm.v1.aes-sha2",
                "ciphertext" to "Y2lwaGVydGV4dA",
                "session_id" to curve,
                "sender_key" to curve,
                "device_id" to key.device,
            )
        val body = json.writeValueAsString(content)
        fun send(
            value: String = body,
            auth: String? = token,
            route: String = "rooms/$room/send/m.room.encrypted/fixture-txn",
            duplicate: Boolean = false,
            suffix: String = "",
        ) =
            uploadKeys(
                n,
                auth,
                value,
                route = route,
                method = "PUT",
                chunked = true,
                duplicate = duplicate,
                suffix = suffix,
            )
        val before = provider.roomSends.get()
        assertEquals(403, send().statusCode())
        val parent = enrolledRoomPeer(n, peer, published = false)
        assertEquals(403, send().statusCode())
        assertEquals(before, provider.roomSends.get())
        assertEquals(
            1,
            database()
                .update(
                    "update collaboration_device_key_publications set version=1,published_at=now() where company_id=? and account_id=?",
                    n.fixture.company,
                    peer,
                ),
        )
        repeat(2) {
            val response = send()
            assertEquals(200, response.statusCode(), response.body())
            assertEquals(
                "\$fixture-sent",
                json.readTree(response.body()).path("event_id").asString(),
            )
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
            assertEquals(json.readTree(body), provider.lastRoomCiphertext.get())
        }
        for (bad in
            listOf(
                "{}",
                json.writeValueAsString(content + ("algorithm" to "plaintext")),
                json.writeValueAsString(content + ("body" to "plaintext")),
                json.writeValueAsString(content + ("ciphertext" to "bad!")),
                body + " {}",
                "{\"algorithm\":\"x\",\"algorithm\":\"y\"}",
            )) assertEquals(400, send(bad).statusCode())
        assertEquals(401, send(auth = n.access).statusCode())
        assertEquals(401, send(duplicate = true).statusCode())
        assertEquals(400, send(suffix = "?access_token=foreign").statusCode())
        assertEquals(
            404,
            send(route = "rooms/!unknown/send/m.room.encrypted/fixture-txn").statusCode(),
        )
        assertEquals(403, send(route = "rooms/$room/send/m.room.message/fixture-txn").statusCode())
        assertEquals(413, send("{\"ciphertext\":\"" + "A".repeat(70_000) + "\"}").statusCode())
        assertEquals(before + 2, provider.roomSends.get())
        provider.onRoomSend.set {
            database()
                .update(
                    "update native_sessions set revoked_at=now(),version=version+1 where id=?",
                    parent,
                )
        }
        val late = send()
        assertEquals(403, late.statusCode(), late.body())
        assertFalse(late.body().contains("event_id"))
        assertEquals(before + 3, provider.roomSends.get())
    }

    @Test
    fun encryptedToDeviceUsesOpaqueAuthorityAndPreservesBoundedCiphertextOverHttp() {
        val n = native()
        val (device, token) = enroll(n)
        val key = fixtureKeys(n, device)
        val body = encryptedBody(key)
        fun send(auth: String?) =
            uploadKeys(
                n,
                auth,
                body,
                chunked = true,
                route = "sendToDevice/m.room.encrypted/fixture-txn",
                method = "PUT",
            )
        for (invalid in listOf(null, n.access, "fictional-provider-token")) assertEquals(
            401,
            send(invalid).statusCode(),
        )
        assertEquals(200, uploadKeys(n, token, json.writeValueAsString(key.body())).statusCode())
        repeat(2) {
            val response = send(token)
            assertEquals(200, response.statusCode(), response.body())
            assertEquals("{}", response.body())
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
            assertEquals(json.readTree(body), provider.lastEnvelope.get())
        }
    }

    @Test
    fun encryptedToDeviceRejectsMalformedAndPlaintextRequestsThenFencesNativeRevocation() {
        val n = native()
        val (device, token) = enroll(n)
        val key = fixtureKeys(n, device)
        val body = encryptedBody(key)
        val before = provider.encryptedSends.get()
        fun send(value: String, route: String = "sendToDevice/m.room.encrypted/fixture-txn") =
            uploadKeys(n, token, value, route = route, method = "PUT")
        for (invalid in
            listOf(
                "{}",
                "{\"messages\":{},\"messages\":{}}",
                body.replace("m.olm.v1.curve25519-aes-sha2", "m.megolm.v1.aes-sha2"),
                body.replace("ZmljdGlvbmFs", "plaintext!"),
                body.replace("matrix.example.test", "foreign.example.test"),
                body.replace(key.device, "*"),
                body + "{}",
            )) {
            assertEquals(400, send(invalid).statusCode())
        }
        assertTrue(send(body, "sendToDevice/m.room_key/fixture-txn").statusCode() in 400..499)
        assertEquals(
            413,
            uploadKeys(
                    n,
                    token,
                    " ".repeat(1_048_577),
                    chunked = true,
                    route = "sendToDevice/m.room.encrypted/fixture-txn",
                    method = "PUT",
                )
                .statusCode(),
        )
        assertEquals(before, provider.encryptedSends.get())
        assertEquals(200, uploadKeys(n, token, json.writeValueAsString(key.body())).statusCode())
        provider.onEncryptedSend.set {
            database()
                .update(
                    "update native_sessions set revoked_at=now(),version=version+1 where id=?",
                    n.session,
                )
        }
        try {
            val response = send(body)
            assertEquals(401, response.statusCode(), response.body())
        } finally {
            provider.onEncryptedSend.set(null)
        }
    }

    @Test
    fun keyClaimRequiresOpaqueAuthorityAndPreservesSignedFieldsAcrossChunkedHttp() {
        val n = native()
        val (device, token) = enroll(n)
        val key = fixtureKeys(n, device)
        val body =
            json.writeValueAsString(
                mapOf(
                    "one_time_keys" to mapOf(key.user to mapOf(key.device to "signed_curve25519")),
                    "timeout" to 10000,
                )
            )
        for (invalid in listOf(null, n.access, "fictional-provider-token")) assertEquals(
            401,
            uploadKeys(n, invalid, body, path = "claim").statusCode(),
        )
        val pending = uploadKeys(n, token, body, path = "claim")
        assertEquals(200, pending.statusCode(), pending.body())
        assertTrue(json.readTree(pending.body()).path("one_time_keys").path(key.user).isEmpty)
        assertEquals(200, uploadKeys(n, token, json.writeValueAsString(key.body())).statusCode())
        val response = uploadKeys(n, token, body, chunked = true, path = "claim")
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
        val result =
            json.readTree(response.body()).path("one_time_keys").path(key.user).path(key.device)
        assertEquals(json.valueToTree(key.body()["one_time_keys"]), result)
        val exhausted = uploadKeys(n, token, body, path = "claim")
        assertEquals(200, exhausted.statusCode(), exhausted.body())
        assertTrue(json.readTree(exhausted.body()).path("one_time_keys").path(key.user).isEmpty)
    }

    @Test
    fun keyClaimRejectsMalformedScopeAndWithholdsKeysAfterCallerRevocation() {
        val n = native()
        val (device, token) = enroll(n)
        val key = fixtureKeys(n, device)
        val body =
            json.writeValueAsString(
                mapOf(
                    "one_time_keys" to mapOf(key.user to mapOf(key.device to "signed_curve25519"))
                )
            )
        val before = provider.keyClaims.get()
        for (invalid in
            listOf(
                "{}",
                "{\"one_time_keys\":null}",
                "{\"one_time_keys\":{},\"one_time_keys\":{}}",
                body.replace("signed_curve25519", "curve25519"),
                body.replace(key.device, "*"),
                body.replace("matrix.example.test", "foreign.example.test"),
                body.dropLast(1) + ",\"timeout\":null}",
                body + "{}",
            )) {
            assertEquals(400, uploadKeys(n, token, invalid, path = "claim").statusCode())
        }
        assertEquals(
            400,
            uploadKeys(n, token, body, suffix = "?access_token=fixture", path = "claim")
                .statusCode(),
        )
        assertEquals(401, uploadKeys(n, token, body, duplicate = true, path = "claim").statusCode())
        assertEquals(
            413,
            uploadKeys(n, token, " ".repeat(65537), chunked = true, path = "claim").statusCode(),
        )
        assertEquals(before, provider.keyClaims.get())
        assertEquals(200, uploadKeys(n, token, json.writeValueAsString(key.body())).statusCode())
        provider.onKeyClaim.set {
            database()
                .update(
                    "update native_sessions set revoked_at=now(),version=version+1 where id=?",
                    n.session,
                )
        }
        try {
            val response = uploadKeys(n, token, body, path = "claim")
            assertEquals(401, response.statusCode(), response.body())
            assertFalse(json.readTree(response.body()).has("one_time_keys"))
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
        } finally {
            provider.onKeyClaim.set(null)
        }
    }

    @Test
    fun keyQueryUsesOpaqueAuthorityAndReturnsOnlyConfirmedExplicitDeviceKeys() {
        val n = native()
        val (device, token) = enroll(n)
        val key = fixtureKeys(n, device)
        val body =
            json.writeValueAsString(
                mapOf("device_keys" to mapOf(key.user to emptyList<String>()), "timeout" to 10000)
            )
        for (invalid in listOf(null, n.access, "fictional-provider-token")) assertEquals(
            401,
            uploadKeys(n, invalid, body, path = "query").statusCode(),
        )
        val pending = uploadKeys(n, token, body, path = "query")
        assertEquals(200, pending.statusCode(), pending.body())
        assertTrue(json.readTree(pending.body()).path("device_keys").path(key.user).isEmpty)
        assertEquals(200, uploadKeys(n, token, json.writeValueAsString(key.body())).statusCode())
        val response = uploadKeys(n, token, body, chunked = true, path = "query")
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
        val keys = json.readTree(response.body()).path("device_keys")
        assertEquals(setOf(key.user), keys.propertyNames().toSet())
        assertEquals(setOf(key.device), keys.path(key.user).propertyNames().toSet())
        assertEquals(key.device, keys.path(key.user).path(key.device).path("device_id").asString())
        assertFalse(keys.path(key.user).path(key.device).has("unsigned"))
        val unknownUser =
            "@hris_${n.fixture.company.toString().replace("-", "")}_${UUID.randomUUID().toString().replace("-", "")}:matrix.example.test"
        val batch =
            uploadKeys(
                n,
                token,
                json.writeValueAsString(
                    mapOf(
                        "device_keys" to
                            mapOf(
                                key.user to emptyList<String>(),
                                unknownUser to emptyList<String>(),
                            )
                    )
                ),
                path = "query",
            )
        assertEquals(200, batch.statusCode(), batch.body())
        assertTrue(json.readTree(batch.body()).path("device_keys").path(unknownUser).isEmpty)
        assertEquals(1, json.readTree(batch.body()).path("device_keys").path(key.user).size())
        val unknown =
            json.writeValueAsString(
                mapOf(
                    "device_keys" to
                        mapOf(
                            key.user to
                                listOf("HRIS_" + UUID.randomUUID().toString().replace("-", ""))
                        )
                )
            )
        val absent = uploadKeys(n, token, unknown, path = "query")
        assertEquals(200, absent.statusCode(), absent.body())
        assertTrue(json.readTree(absent.body()).path("device_keys").path(key.user).isEmpty)
    }

    private fun reserveFixtureKeys(n: Native, device: UUID, key: KeyPayload) {
        val keys =
            requireNotNull(
                matrixKeyUploadRequest(json.readTree(json.writeValueAsString(key.body())))
                    .deviceKeys
            )
        val fingerprint = validateCollaborationDeviceKeys(keys, key.user, key.device)
        assertInstanceOf(Result.Success::class.java, fingerprint)
        database()
            .update(
                "insert into collaboration_device_key_publications(company_id,device_id,account_id,fingerprint,version,reserved_at) values(?,?,?,?,0,now())",
                n.fixture.company,
                device,
                n.fixture.account,
                (fingerprint as Result.Success).value,
            )
    }

    private fun queryFixtureKeys(n: Native, token: String, key: KeyPayload) =
        uploadKeys(
            n,
            token,
            json.writeValueAsString(mapOf("device_keys" to mapOf(key.user to emptyList<String>()))),
            path = "query",
        )

    private fun confirmFixtureKeys(device: UUID) {
        assertEquals(
            1,
            database()
                .update(
                    "update collaboration_device_key_publications set version=1,published_at=now() where device_id=? and version=0",
                    device,
                ),
        )
    }

    @Test
    fun keyQuerySeesPinnedProviderPublicationBeforePublisherReadbackCommits() {
        val n = native()
        val (device, token) = enroll(n)
        val key = fixtureKeys(n, device)
        reserveFixtureKeys(n, device, key)
        val absent = queryFixtureKeys(n, token, key)
        assertEquals(200, absent.statusCode(), absent.body())
        assertTrue(json.readTree(absent.body()).path("device_keys").path(key.user).isEmpty)
        // Exact interleaving: the provider now exposes keys and emits its change event,
        // while HRIS still retains the original unconfirmed reservation.
        provider.putDeviceKeys(key)
        val concurrent = queryFixtureKeys(n, token, key)
        assertEquals(200, concurrent.statusCode(), concurrent.body())
        assertEquals(
            setOf(key.device),
            json
                .readTree(concurrent.body())
                .path("device_keys")
                .path(key.user)
                .propertyNames()
                .toSet(),
        )
        assertEquals(
            0L,
            database()
                .queryForObject(
                    "select version from collaboration_device_key_publications where device_id=?",
                    Long::class.java,
                    device,
                ),
        )
        confirmFixtureKeys(device)
        assertEquals(
            json.readTree(concurrent.body()),
            json.readTree(queryFixtureKeys(n, token, key).body()),
        )
        provider.removeDeviceKeys(key)
        val missingConfirmed = queryFixtureKeys(n, token, key)
        assertEquals(503, missingConfirmed.statusCode(), missingConfirmed.body())
        assertFalse(json.readTree(missingConfirmed.body()).has("device_keys"))
    }

    @Test
    fun reservedAndConfirmedCandidatesRejectDifferentValidlySignedProviderKeys() {
        val n = native()
        val (device, token) = enroll(n)
        val expected = fixtureKeys(n, device)
        reserveFixtureKeys(n, device, expected)
        val changed = fixtureKeys(n, device)
        for (confirmed in listOf(false, true)) {
            if (confirmed) confirmFixtureKeys(device)
            provider.putDeviceKeys(changed)
            val denied = queryFixtureKeys(n, token, expected)
            assertEquals(409, denied.statusCode(), denied.body())
            assertFalse(json.readTree(denied.body()).has("device_keys"))
            provider.putDeviceKeys(expected)
            assertEquals(200, queryFixtureKeys(n, token, expected).statusCode())
        }
    }

    @Test
    fun publicationTransitionWithholdsAnObsoleteQueryAndAllowsAnExplicitRetry() {
        val n = native()
        val (device, token) = enroll(n)
        val key = fixtureKeys(n, device)
        reserveFixtureKeys(n, device, key)
        provider.putDeviceKeys(key)
        provider.onKeyQuery.set { confirmFixtureKeys(device) }
        try {
            val changed = queryFixtureKeys(n, token, key)
            assertEquals(503, changed.statusCode(), changed.body())
            assertFalse(json.readTree(changed.body()).has("device_keys"))
            assertEquals(200, queryFixtureKeys(n, token, key).statusCode())
        } finally {
            provider.onKeyQuery.set(null)
        }
    }

    @Test
    fun earlyKeyDiscoveryDoesNotGrantClaimUntilPublicationIsConfirmed() {
        val n = native()
        val (device, token) = enroll(n)
        val key = fixtureKeys(n, device)
        reserveFixtureKeys(n, device, key)
        val body =
            json.writeValueAsString(
                mapOf(
                    "one_time_keys" to mapOf(key.user to mapOf(key.device to "signed_curve25519"))
                )
            )
        provider.putDeviceKeys(key)
        val discovered = queryFixtureKeys(n, token, key)
        assertEquals(200, discovered.statusCode(), discovered.body())
        assertEquals(1, json.readTree(discovered.body()).path("device_keys").path(key.user).size())
        val before = provider.keyClaims.get()
        val queried = provider.keyQueries.get()
        val absent = uploadKeys(n, token, body, path = "claim")
        assertEquals(200, absent.statusCode(), absent.body())
        assertTrue(json.readTree(absent.body()).path("one_time_keys").path(key.user).isEmpty)
        assertEquals(before, provider.keyClaims.get())
        assertEquals(queried, provider.keyQueries.get())
        confirmFixtureKeys(device)
        provider.putDeviceKeys(fixtureKeys(n, device))
        assertEquals(409, uploadKeys(n, token, body, path = "claim").statusCode())
        assertEquals(before, provider.keyClaims.get())
        provider.putDeviceKeys(key)
        val available = uploadKeys(n, token, body, path = "claim")
        assertEquals(200, available.statusCode(), available.body())
        assertEquals(before + 1, provider.keyClaims.get())
        // There are no uploaded one-time keys. A valid identity alone is not delivery evidence.
        assertTrue(json.readTree(available.body()).path("one_time_keys").path(key.user).isEmpty)
    }

    @Test
    fun pendingPublicationCannotBypassNativeParentRevocation() {
        val n = native()
        val (device, token) = enroll(n)
        val key = fixtureKeys(n, device)
        reserveFixtureKeys(n, device, key)
        provider.putDeviceKeys(key)
        provider.onKeyQuery.set {
            database()
                .update(
                    "update native_sessions set revoked_at=now(),version=version+1 where id=?",
                    n.session,
                )
        }
        try {
            val denied = queryFixtureKeys(n, token, key)
            assertEquals(401, denied.statusCode(), denied.body())
            assertFalse(json.readTree(denied.body()).has("device_keys"))
        } finally {
            provider.onKeyQuery.set(null)
        }
    }

    @Test
    fun keyQueryRejectsAmbiguousOrForeignRequestsWithoutCallingTheProvider() {
        val n = native()
        val (device, token) = enroll(n)
        val key = fixtureKeys(n, device)
        val body =
            json.writeValueAsString(mapOf("device_keys" to mapOf(key.user to emptyList<String>())))
        val before = provider.keyQueries.get()
        for (invalid in
            listOf(
                "{}",
                "{\"device_keys\":null}",
                "{\"device_keys\":{},\"device_keys\":{}}",
                body.dropLast(1) + ",\"timeout\":null}",
                body.dropLast(1) + ",\"timeout\":-1}",
                body.dropLast(1) + ",\"unknown\":true}",
                body.replace("matrix.example.test", "foreign.example.test"),
                body.replace("[]", "[\"*\"]"),
            )) {
            assertEquals(400, uploadKeys(n, token, invalid, path = "query").statusCode())
        }
        assertEquals(
            400,
            uploadKeys(n, token, body, suffix = "?access_token=fixture", path = "query")
                .statusCode(),
        )
        assertEquals(401, uploadKeys(n, token, body, duplicate = true, path = "query").statusCode())
        assertEquals(
            413,
            uploadKeys(n, token, " ".repeat(65537), chunked = true, path = "query").statusCode(),
        )
        assertEquals(before, provider.keyQueries.get())
    }

    @Test
    fun keyQueryWithholdsAProviderResultAfterNativeRevocation() {
        val n = native()
        val (device, token) = enroll(n)
        val key = fixtureKeys(n, device)
        assertEquals(200, uploadKeys(n, token, json.writeValueAsString(key.body())).statusCode())
        provider.onKeyQuery.set {
            database()
                .update(
                    "update native_sessions set revoked_at=now(),version=version+1 where id=?",
                    n.session,
                )
        }
        try {
            val response =
                uploadKeys(
                    n,
                    token,
                    json.writeValueAsString(
                        mapOf("device_keys" to mapOf(key.user to emptyList<String>()))
                    ),
                    path = "query",
                )
            assertEquals(401, response.statusCode(), response.body())
            assertFalse(json.readTree(response.body()).has("device_keys"))
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
            assertEquals(
                1,
                database()
                    .queryForObject(
                        "select count(*) from collaboration_device_key_publications where device_id=? and version=1",
                        Int::class.java,
                        device,
                    ),
            )
        } finally {
            provider.onKeyQuery.set(null)
        }
    }

    @Test
    fun keyUploadUsesOpaqueAuthorityPreservesSignedFieldsAndRetainsPublicationOnReplay() {
        val n = native()
        val (device, token) = enroll(n)
        val before = provider.keyUploads.get()
        for (invalid in listOf(null, n.access, "fictional-provider-token")) assertEquals(
            401,
            uploadKeys(n, invalid).statusCode(),
        )
        assertEquals(before, provider.keyUploads.get())
        val empty = uploadKeys(n, token)
        assertEquals(200, empty.statusCode(), empty.body())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_device_key_publications where device_id=?",
                    Int::class.java,
                    device,
                ),
        )
        val payload = fixtureKeys(n, device)
        val body = json.writeValueAsString(payload.body())
        repeat(2) { attempt ->
            val result = uploadKeys(n, token, body, chunked = attempt == 1)
            assertEquals(200, result.statusCode(), result.body())
            assertEquals("no-store", result.headers().firstValue("Cache-Control").orElse(""))
            val value = json.readTree(result.body())
            assertEquals(setOf("one_time_key_counts"), value.propertyNames().toSet())
            assertEquals(1, value.path("one_time_key_counts").path("signed_curve25519").asInt())
            assertFalse(result.body().contains(token))
        }
        assertEquals(
            1L,
            database()
                .queryForObject(
                    "select version from collaboration_device_key_publications where device_id=?",
                    Long::class.java,
                    device,
                ),
        )
        val replacement = fixtureKeys(n, device)
        val prior = provider.keyUploads.get()
        val changed = uploadKeys(n, token, json.writeValueAsString(replacement.body()))
        assertEquals(409, changed.statusCode(), changed.body())
        assertEquals(
            "collaboration_device_key_recovery_required",
            json.readTree(changed.body()).path("org.hris.error_code").asString(),
        )
        assertEquals(prior, provider.keyUploads.get())
    }

    @Test
    fun keyUploadRejectsAmbiguousHeadersUnknownFieldsAndOversizeStreamsBeforeProvider() {
        val n = native()
        val (device, token) = enroll(n)
        val before = provider.keyUploads.get()
        assertEquals(401, uploadKeys(n, token, duplicate = true).statusCode())
        assertEquals(400, uploadKeys(n, token, suffix = "?access_token=not-a-token").statusCode())
        assertEquals(415, uploadKeys(n, token, contentType = "text/plain").statusCode())
        for (body in
            listOf(
                "null",
                "[]",
                "{}{}",
                """{"private_key":"forbidden"}""",
                """{"device_keys":null}""",
                """{"one_time_keys":null}""",
                """{"one_time_keys":{},"one_time_keys":{}}""",
                """{"one_time_keys":{"signed_curve25519:1":{"key":"x","signatures":{},"fallback":null}}}""",
            )) {
            val response = uploadKeys(n, token, body)
            assertEquals(400, response.statusCode(), response.body())
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
        }
        for (chunked in listOf(false, true)) assertEquals(
            413,
            uploadKeys(n, token, " ".repeat(65537), chunked = chunked).statusCode(),
        )
        val wrong =
            KeyPayload("@foreign:matrix.example.test", "HRIS_${device.toString().replace("-", "")}")
        assertEquals(400, uploadKeys(n, token, json.writeValueAsString(wrong.body())).statusCode())
        assertEquals(before, provider.keyUploads.get())
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_device_key_publications where device_id=?",
                    Int::class.java,
                    device,
                ),
        )
    }

    @Test
    fun keyUploadWithholdsLateProviderSuccessAfterNativeRevocationAndKeepsPendingIdentity() {
        val n = native()
        val (device, token) = enroll(n)
        val body = json.writeValueAsString(fixtureKeys(n, device).body())
        provider.onKeyUpload.set {
            database()
                .update(
                    "update native_sessions set revoked_at=now(),version=version+1 where id=?",
                    n.session,
                )
        }
        try {
            val response = uploadKeys(n, token, body)
            assertEquals(401, response.statusCode(), response.body())
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
            assertEquals(
                0L,
                database()
                    .queryForObject(
                        "select version from collaboration_device_key_publications where device_id=?",
                        Long::class.java,
                        device,
                    ),
            )
            val count = provider.keyUploads.get()
            assertEquals(401, uploadKeys(n, token, body).statusCode())
            assertEquals(count, provider.keyUploads.get())
        } finally {
            provider.onKeyUpload.set(null)
        }
    }

    private class KeyPayload(val user: String, val device: String) {
        private val json = JsonMapper.builder().build()
        private val signing =
            java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        private val encoder = Base64.getEncoder().withoutPadding()

        private fun publicCurve() =
            encoder.encodeToString(
                java.security.KeyPairGenerator.getInstance("X25519")
                    .generateKeyPair()
                    .public
                    .encoded
                    .takeLast(32)
                    .toByteArray()
            )

        private fun signed(body: Map<String, Any>): Map<String, Any> {
            val proof =
                java.security.Signature.getInstance("Ed25519").run {
                    initSign(signing.private)
                    update(json.writeValueAsBytes(body.toSortedMap()))
                    encoder.encodeToString(sign())
                }
            return body + ("signatures" to mapOf(user to mapOf("ed25519:$device" to proof)))
        }

        private val deviceKeys =
            signed(
                mapOf(
                    "user_id" to user,
                    "device_id" to device,
                    "algorithms" to listOf("m.olm.v1.curve25519-aes-sha2", "m.megolm.v1.aes-sha2"),
                    "keys" to
                        sortedMapOf(
                            "ed25519:$device" to
                                encoder.encodeToString(
                                    signing.public.encoded.takeLast(32).toByteArray()
                                ),
                            "curve25519:$device" to publicCurve(),
                        ),
                )
            )
        private val oneTime = signed(mapOf("key" to publicCurve(), "fallback" to false))
        private val fallback = signed(mapOf("key" to publicCurve(), "fallback" to true))

        fun body() =
            mapOf(
                "device_keys" to deviceKeys,
                "one_time_keys" to mapOf("signed_curve25519:first" to oneTime),
                "fallback_keys" to mapOf("signed_curve25519:fallback" to fallback),
            )
    }

    private class Provider : AutoCloseable {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val pushes = ConcurrentLinkedQueue<tools.jackson.databind.JsonNode>()
        val keyUploads = AtomicInteger()
        val keyQueries = AtomicInteger()
        val keyClaims = AtomicInteger()
        val encryptedSends = AtomicInteger()
        val roomSends = AtomicInteger()
        val lastRoomCiphertext = AtomicReference<tools.jackson.databind.JsonNode>()
        val onRoomSend = AtomicReference<(() -> Unit)?>(null)
        val onEncryptedSend = AtomicReference<(() -> Unit)?>(null)
        val lastEnvelope = AtomicReference<tools.jackson.databind.JsonNode>()
        val onKeyClaim = AtomicReference<(() -> Unit)?>(null)
        private val claimable =
            ConcurrentHashMap<Pair<String, String>, tools.jackson.databind.JsonNode>()
        val onKeyQuery = AtomicReference<(() -> Unit)?>(null)
        val onKeyUpload = AtomicReference<(() -> Unit)?>(null)
        private val keys =
            ConcurrentHashMap<Pair<String, String>, tools.jackson.databind.JsonNode>()

        fun putDeviceKeys(value: KeyPayload) {
            keys[value.user to value.device] =
                parser.readTree(parser.writeValueAsString(value.body())).path("device_keys")
        }

        fun removeDeviceKeys(value: KeyPayload) {
            keys.remove(value.user to value.device)
        }

        private val keyIds = ConcurrentHashMap<Pair<String, String>, MutableSet<String>>()
        val logins = AtomicInteger()
        val queries = AtomicInteger()
        val syncs = AtomicInteger()
        val onSync = AtomicReference<(() -> Unit)?>(null)
        val roomStates = ConcurrentHashMap<String, List<Map<String, Any>>>()
        val roomReceipts = java.util.concurrent.atomic.AtomicInteger()
        val roomCalls = AtomicInteger()
        val lastMemberQuery = AtomicReference<String>()
        val lastHistoryQuery = AtomicReference<String>()
        val lastContextQuery = AtomicReference<String>()
        val onRoomHistory = AtomicReference<(() -> Unit)?>(null)
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
                            "/_matrix/client/v3/pushers/set" -> {
                                assertEquals("POST", request.requestMethod)
                                assertNull(request.requestURI.rawQuery)
                                val bearer =
                                    request.requestHeaders
                                        .getFirst("Authorization")
                                        .removePrefix("Bearer ")
                                assertTrue(credentials.containsKey(bearer))
                                val body = parser.readTree(request.requestBody.readNBytes(8193))
                                pushes.add(body)
                                emptyMap<String, Any>()
                            }
                            "/_matrix/client/v3/keys/query" -> {
                                keyQueries.incrementAndGet()
                                val identity =
                                    requireNotNull(
                                        credentials[
                                            request.requestHeaders
                                                .getFirst("Authorization")
                                                .removePrefix("Bearer ")]
                                    )
                                val user = identity["user_id"] as String
                                val device = identity["device_id"] as String
                                val body = parser.readTree(request.requestBody.readNBytes(65537))
                                assertEquals(
                                    setOf(user),
                                    body.path("device_keys").propertyNames().toSet(),
                                )
                                assertEquals(
                                    listOf(device),
                                    body
                                        .path("device_keys")
                                        .path(user)
                                        .iterator()
                                        .asSequence()
                                        .map { it.asString() }
                                        .toList(),
                                )
                                onKeyQuery.getAndSet(null)?.invoke()
                                mapOf(
                                    "device_keys" to
                                        mapOf(
                                            user to
                                                (keys[user to device]?.let { mapOf(device to it) }
                                                    ?: emptyMap())
                                        )
                                )
                            }
                            "/_matrix/client/v3/sendToDevice/m.room.encrypted/fixture-txn" -> {
                                encryptedSends.incrementAndGet()
                                assertEquals("PUT", request.requestMethod)
                                val identity =
                                    requireNotNull(
                                        credentials[
                                            request.requestHeaders
                                                .getFirst("Authorization")
                                                .removePrefix("Bearer ")]
                                    )
                                val body =
                                    parser.readTree(request.requestBody.readNBytes(1_048_577))
                                assertEquals(
                                    setOf(identity["user_id"]),
                                    body.path("messages").propertyNames().toSet(),
                                )
                                lastEnvelope.set(body)
                                onEncryptedSend.getAndSet(null)?.invoke()
                                emptyMap<String, Any>()
                            }
                            "/_matrix/client/v3/keys/claim" -> {
                                keyClaims.incrementAndGet()
                                val identity =
                                    requireNotNull(
                                        credentials[
                                            request.requestHeaders
                                                .getFirst("Authorization")
                                                .removePrefix("Bearer ")]
                                    )
                                val user = identity["user_id"] as String
                                val device = identity["device_id"] as String
                                val body = parser.readTree(request.requestBody.readNBytes(65537))
                                assertEquals(
                                    setOf(user),
                                    body.path("one_time_keys").propertyNames().toSet(),
                                )
                                assertEquals(
                                    setOf(device),
                                    body.path("one_time_keys").path(user).propertyNames().toSet(),
                                )
                                assertEquals(
                                    "signed_curve25519",
                                    body.path("one_time_keys").path(user).path(device).asString(),
                                )
                                val selected = claimable.remove(user to device)
                                onKeyClaim.getAndSet(null)?.invoke()
                                mapOf(
                                    "one_time_keys" to
                                        mapOf(
                                            user to
                                                (selected?.let { mapOf(device to it) }
                                                    ?: emptyMap())
                                        )
                                )
                            }
                            "/_matrix/client/v3/keys/upload" -> {
                                keyUploads.incrementAndGet()
                                val identity =
                                    requireNotNull(
                                        credentials[
                                            request.requestHeaders
                                                .getFirst("Authorization")
                                                .removePrefix("Bearer ")]
                                    )
                                val user = identity["user_id"] as String
                                val device = identity["device_id"] as String
                                val body = parser.readTree(request.requestBody.readNBytes(65537))
                                body.get("device_keys")?.let { keys[user to device] = it }
                                val ids =
                                    keyIds.getOrPut(user to device) {
                                        ConcurrentHashMap.newKeySet()
                                    }
                                body.get("one_time_keys")?.let {
                                    ids.addAll(it.propertyNames())
                                    claimable[user to device] = it
                                }
                                onKeyUpload.getAndSet(null)?.invoke()
                                mapOf(
                                    "one_time_key_counts" to mapOf("signed_curve25519" to ids.size)
                                )
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
                            "/_matrix/client/v3/sync" -> {
                                assertNotNull(
                                    credentials[
                                        request.requestHeaders
                                            .getFirst("Authorization")
                                            .removePrefix("Bearer ")]
                                )
                                syncs.incrementAndGet()
                                onSync.getAndSet(null)?.invoke()
                                mapOf(
                                    "next_batch" to "fixture-private-since",
                                    "rooms" to emptyMap<String, Any>(),
                                )
                            }
                            else -> {
                                if (
                                    request.requestURI.path.startsWith("/_matrix/client/v3/rooms/")
                                ) {
                                    assertNotNull(
                                        credentials[
                                            request.requestHeaders
                                                .getFirst("Authorization")
                                                .removePrefix("Bearer ")]
                                    )
                                    val relative =
                                        request.requestURI.path.removePrefix(
                                            "/_matrix/client/v3/rooms/"
                                        )
                                    val room = relative.substringBefore('/')
                                    val events = requireNotNull(roomStates[room])
                                    roomCalls.incrementAndGet()
                                    when (relative.substringAfter('/')) {
                                        "send/m.room.encrypted/fixture-txn" -> {
                                            assertEquals("PUT", request.requestMethod)
                                            roomSends.incrementAndGet()
                                            lastRoomCiphertext.set(
                                                parser.readTree(
                                                    request.requestBody.readNBytes(65537)
                                                )
                                            )
                                            onRoomSend.getAndSet(null)?.invoke()
                                            mapOf("event_id" to "\$fixture-sent")
                                        }
                                        "join" -> mapOf("room_id" to room)
                                        "state" -> events
                                        "members" -> {
                                            lastMemberQuery.set(request.requestURI.query)
                                            mapOf(
                                                "chunk" to
                                                    events.filter { it["type"] == "m.room.member" }
                                            )
                                        }
                                        else -> {
                                            val operation = relative.substringAfter('/')
                                            onRoomHistory.getAndSet(null)?.invoke()
                                            when {
                                                operation.startsWith("receipt/") -> {
                                                    assertEquals("POST", request.requestMethod)
                                                    assertEquals(
                                                        "{}",
                                                        request.requestBody
                                                            .readNBytes(257)
                                                            .toString(Charsets.UTF_8),
                                                    )
                                                    val pieces = operation.split('/')
                                                    assertEquals(3, pieces.size)
                                                    assertTrue(
                                                        pieces[1] in
                                                            setOf("m.read", "m.read.private")
                                                    )
                                                    assertTrue(
                                                        events.any { it["event_id"] == pieces[2] }
                                                    )
                                                    roomReceipts.incrementAndGet()
                                                    emptyMap<String, Any>()
                                                }
                                                operation == "messages" -> {
                                                    lastHistoryQuery.set(request.requestURI.query)
                                                    if (request.requestURI.query.contains("from="))
                                                        mapOf(
                                                            "start" to "private-history-1",
                                                            "chunk" to emptyList<Any>(),
                                                        )
                                                    else
                                                        mapOf(
                                                            "start" to "private-history-2",
                                                            "end" to "private-history-1",
                                                            "chunk" to events,
                                                        )
                                                }
                                                operation.startsWith("event/") ->
                                                    events.single {
                                                        it["event_id"] ==
                                                            operation.removePrefix("event/")
                                                    }
                                                operation.startsWith("context/") -> {
                                                    lastContextQuery.set(request.requestURI.query)
                                                    mapOf(
                                                        "event" to
                                                            events.single {
                                                                it["event_id"] ==
                                                                    operation.removePrefix(
                                                                        "context/"
                                                                    )
                                                            },
                                                        "events_before" to emptyList<Any>(),
                                                        "events_after" to emptyList<Any>(),
                                                        "state" to
                                                            events.filter {
                                                                it["type"] !in
                                                                    setOf(
                                                                        "m.room.encrypted",
                                                                        "m.room.redaction",
                                                                    )
                                                            },
                                                        "start" to "private-history-1",
                                                        "end" to "private-history-2",
                                                    )
                                                }
                                                else -> error("Unexpected room fixture route")
                                            }
                                        }
                                    }
                                } else if (
                                    request.requestURI.path.startsWith(
                                        "/_matrix/client/v3/user/"
                                    ) && request.requestURI.path.endsWith("/filter")
                                ) {
                                    val identity =
                                        requireNotNull(
                                            credentials[
                                                request.requestHeaders
                                                    .getFirst("Authorization")
                                                    .removePrefix("Bearer ")]
                                        )
                                    assertEquals(
                                        "/_matrix/client/v3/user/${identity["user_id"]}/filter",
                                        request.requestURI.path,
                                    )
                                    val filter =
                                        parser.readTree(request.requestBody.readNBytes(65537))
                                    assertFalse(filter.path("room").has("rooms"))
                                    mapOf("filter_id" to "0")
                                } else error("Unexpected fixed provider route")
                            }
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
