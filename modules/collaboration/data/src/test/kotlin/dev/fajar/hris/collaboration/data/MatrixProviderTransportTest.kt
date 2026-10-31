package dev.fajar.hris.collaboration.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationMatrixKeyRepository
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationMatrixRepository
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationMatrixRoomRepository
import dev.fajar.hris.collaboration.data.repositories.InternalCollaborationMatrixToDeviceRepository
import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixDirectRoom
import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixKeyClaim
import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixKeyQuery
import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixKeyUpload
import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixLogin
import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixRoomCreation
import dev.fajar.hris.collaboration.domain.entities.CollaborationMatrixRoomSetting
import dev.fajar.hris.collaboration.domain.entities.CollaborationOlmEnvelope
import dev.fajar.hris.collaboration.domain.entities.CollaborationToDevice
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.Result
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.nio.ByteBuffer
import java.time.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class MatrixProviderTransportTest {
    @Test
    fun encryptedToDevicePreservesFixedRouteAndEnvelopeBeyondKeyRequestSizeBound() {
        Fixture().use { f ->
            val key = MatrixKeyFixture(f.userId, collaborationMatrixDeviceId(f.login.deviceId))
            val curve = key.device.keys.getValue("curve25519:${key.deviceId}")
            val content =
                CollaborationOlmEnvelope(curve, curve, "A".repeat(65536), 0, "fixture-message")
            val send =
                CollaborationToDevice(
                    "fixture~transaction",
                    mapOf(key.userId to mapOf(key.deviceId to content)),
                )
            var response = "{}"
            f.handler = { exchange ->
                assertEquals("PUT", exchange.requestMethod)
                assertEquals(
                    "/_matrix/client/v3/sendToDevice/m.room.encrypted/fixture~transaction",
                    exchange.requestURI.path,
                )
                assertNull(exchange.requestURI.rawQuery)
                val bytes = exchange.requestBody.readNBytes(1_048_577)
                assertTrue(bytes.size > 65536)
                val body =
                    f.json.readTree(bytes).path("messages").path(key.userId).path(key.deviceId)
                assertEquals("m.olm.v1.curve25519-aes-sha2", body.path("algorithm").asString())
                assertEquals(
                    content.body,
                    body.path("ciphertext").path(curve).path("body").asString(),
                )
                assertEquals("fixture-message", body.path("org.matrix.msgid").asString())
                f.respond(exchange, response)
            }
            val repo = InternalCollaborationMatrixToDeviceRepository(f.source)
            fun deliver() = repo.send(send, "fictional-access-token", Instant.now().plusSeconds(10))
            assertEquals(Result.Success(Unit), deliver())
            for (bad in listOf("[]", "{\"unexpected\":true}", "x".repeat(65537))) {
                response = bad
                assertInstanceOf(Result.Failed::class.java, deliver())
            }
            response = "{}"
            assertEquals(Result.Success(Unit), deliver())
        }
    }

    @Test
    fun claimUsesFixedSignedExplicitTransportAndRejectsForeignOrMalformedResponses() {
        Fixture().use { f ->
            val device = collaborationMatrixDeviceId(f.login.deviceId)
            val key = MatrixKeyFixture(f.userId, device).signed(true)
            val signed = mapOf("key" to key.key, "signatures" to key.signatures, "fallback" to true)
            fun body(
                user: String = f.userId,
                id: String = device,
                value: Any = mapOf("signed_curve25519:1" to signed),
            ) = mapOf("one_time_keys" to mapOf(user to mapOf(id to value)))
            val request =
                CollaborationMatrixKeyClaim(mapOf(f.userId to mapOf(device to "signed_curve25519")))
            val repo = InternalCollaborationMatrixKeyRepository(f.source)
            fun claim() =
                repo.claimKeys(request, "fictional-access-token", Instant.now().plusSeconds(10))
            var response: Any = body()
            f.handler = { exchange ->
                assertEquals("POST", exchange.requestMethod)
                assertEquals("/_matrix/client/v3/keys/claim", exchange.requestURI.path)
                assertNull(exchange.requestURI.rawQuery)
                assertEquals(
                    "Bearer fictional-access-token",
                    exchange.requestHeaders.getFirst("Authorization"),
                )
                val received = f.json.readTree(exchange.requestBody.readNBytes(65537))
                assertEquals(
                    "signed_curve25519",
                    received.path("one_time_keys").path(f.userId).path(device).asString(),
                )
                assertEquals(1000, received.path("timeout").asInt())
                f.respond(exchange, f.json.writeValueAsString(response))
            }
            assertEquals(
                key,
                (claim() as Result.Success)
                    .value
                    .oneTimeKeys
                    .getValue(f.userId)
                    .getValue(device)
                    .getValue("signed_curve25519:1"),
            )
            for (invalid in
                listOf(
                    body(user = "@foreign:elsewhere.test"),
                    body(id = "FOREIGN"),
                    body(
                        value =
                            mapOf("signed_curve25519:1" to signed, "signed_curve25519:2" to signed)
                    ),
                    body(value = mapOf("curve25519:1" to key.key)),
                    body(value = mapOf("signed_curve25519:1" to (signed + ("fallback" to "true")))),
                    body() + ("failures" to mapOf("foreign" to emptyMap<String, Any>())),
                )) {
                response = invalid
                assertInstanceOf(Result.Failed::class.java, claim())
            }
            response = mapOf("one_time_keys" to emptyMap<String, Any>())
            assertTrue((claim() as Result.Success).value.oneTimeKeys.getValue(f.userId).isEmpty())
            f.handler = { exchange -> f.respond(exchange, "x".repeat(1_048_577)) }
            assertInstanceOf(Result.Failed::class.java, claim())
            f.handler = { exchange -> f.respond(exchange, f.json.writeValueAsString(body())) }
            assertInstanceOf(Result.Success::class.java, claim())
        }
    }

    @Test
    fun claimRejectsWildcardUnsignedAndOversizedRequestsBeforeTransport() {
        Fixture().use { f ->
            val calls = AtomicInteger()
            f.handler = { exchange ->
                calls.incrementAndGet()
                f.respond(exchange, "{}")
            }
            val repo = InternalCollaborationMatrixKeyRepository(f.source)
            val device = collaborationMatrixDeviceId(f.login.deviceId)
            val selections =
                listOf(
                    emptyMap(),
                    mapOf(f.userId to emptyMap()),
                    mapOf(f.userId to mapOf(device to "curve25519")),
                    (0..100).associate {
                        "@user$it:example.test" to mapOf(device to "signed_curve25519")
                    },
                )
            for (selection in selections) assertInstanceOf(
                Result.Failed::class.java,
                repo.claimKeys(
                    CollaborationMatrixKeyClaim(selection),
                    "fictional-access-token",
                    Instant.now().plusSeconds(10),
                ),
            )
            assertEquals(0, calls.get())
        }
    }

    @Test
    fun explicitRecipientQueryHasItsOwnBoundAndNeverRequestsAllProviderDevices() {
        Fixture().use { f ->
            val keys =
                (1..100).map {
                    MatrixKeyFixture(
                        "@${collaborationMatrixLocalpart(f.login.companyId, UUID.randomUUID())}:matrix.example.test",
                        collaborationMatrixDeviceId(UUID.randomUUID()),
                    )
                }
            val selected =
                CollaborationMatrixKeyQuery(keys.associate { it.userId to listOf(it.deviceId) })
            val body =
                f.json.writeValueAsBytes(
                    mapOf(
                        "device_keys" to
                            keys.associate { key ->
                                key.userId to
                                    mapOf(
                                        key.deviceId to
                                            (key.body() +
                                                ("unsigned" to
                                                    mapOf(
                                                        "device_display_name" to "x".repeat(256)
                                                    )))
                                    )
                            }
                    )
                )
            assertTrue(body.size > 65536 && body.size < 1_048_576)
            f.handler = { exchange ->
                assertEquals("POST", exchange.requestMethod)
                assertEquals("/_matrix/client/v3/keys/query", exchange.requestURI.path)
                assertNull(exchange.requestURI.rawQuery)
                assertEquals(
                    "Bearer fictional-access-token",
                    exchange.requestHeaders.getFirst("Authorization"),
                )
                val request = f.json.readTree(exchange.requestBody.readNBytes(65537))
                assertEquals(100, request.path("device_keys").size())
                assertTrue(
                    request.path("device_keys").properties().all { (_, ids) ->
                        ids.isArray && ids.size() == 1
                    }
                )
                assertEquals(1000, request.path("timeout").asInt())
                f.respond(exchange, body.toString(Charsets.UTF_8))
            }
            val repo = InternalCollaborationMatrixKeyRepository(f.source)
            val result =
                repo.deviceKeys(selected, "fictional-access-token", Instant.now().plusSeconds(10))
            assertInstanceOf(Result.Success::class.java, result)
            val actual = (result as Result.Success).value.deviceKeys
            assertEquals(100, actual.size)
            for (key in keys) assertEquals(
                key.device,
                actual.getValue(key.userId).getValue(key.deviceId),
            )
        }
    }

    @Test
    fun recipientQueryRejectsForeignResultsAndOversizeBodiesThenReleasesTransport() {
        Fixture().use { f ->
            val key = MatrixKeyFixture(f.userId, collaborationMatrixDeviceId(f.login.deviceId))
            val query = CollaborationMatrixKeyQuery(mapOf(key.userId to listOf(key.deviceId)))
            val valid =
                mapOf("device_keys" to mapOf(key.userId to mapOf(key.deviceId to key.body())))
            val repo = InternalCollaborationMatrixKeyRepository(f.source)
            fun read() =
                repo.deviceKeys(query, "fictional-access-token", Instant.now().plusSeconds(10))
            val responses =
                listOf(
                    mapOf(
                        "device_keys" to
                            mapOf("@foreign:example.test" to mapOf(key.deviceId to key.body()))
                    ),
                    mapOf(
                        "device_keys" to mapOf(key.userId to mapOf("FOREIGN_DEVICE" to key.body()))
                    ),
                    mapOf(
                        "device_keys" to
                            mapOf(
                                key.userId to
                                    mapOf(
                                        key.deviceId to
                                            (key.body() + ("device_id" to "FOREIGN_DEVICE"))
                                    )
                            )
                    ),
                    valid + ("failures" to mapOf("foreign.example.test" to emptyMap<String, Any>())),
                )
            for (response in responses) {
                f.handler = { f.respond(it, f.json.writeValueAsString(response)) }
                assertInstanceOf(Result.Failed::class.java, read())
            }
            f.handler = { f.respond(it, " ".repeat(1_048_577)) }
            assertInstanceOf(Result.Failed::class.java, read())
            f.handler = { f.respond(it, f.json.writeValueAsString(valid)) }
            assertInstanceOf(Result.Success::class.java, read())
            f.handler = {
                f.respond(
                    it,
                    f.json.writeValueAsString(mapOf("device_keys" to emptyMap<String, Any>())),
                )
            }
            assertEquals(
                emptyMap<String, Any>(),
                (read() as Result.Success).value.deviceKeys.getValue(key.userId),
            )
        }
    }

    @Test
    fun recipientQueryRefusesEmptyDeviceWildcardsBeforeTransport() {
        Fixture().use { f ->
            val calls = AtomicInteger()
            f.handler = {
                calls.incrementAndGet()
                f.respond(it, f.success)
            }
            val repo = InternalCollaborationMatrixKeyRepository(f.source)
            for (query in
                listOf(
                    emptyMap(),
                    mapOf(f.userId to emptyList()),
                    mapOf(f.userId to List(6) { collaborationMatrixDeviceId(UUID.randomUUID()) }),
                    (1..101).associate { "@user$it:proof.invalid" to listOf("DEVICE") },
                )) {
                assertInstanceOf(
                    Result.Failed::class.java,
                    repo.deviceKeys(
                        CollaborationMatrixKeyQuery(query),
                        "fictional-access-token",
                        Instant.now().plusSeconds(10),
                    ),
                )
            }
            assertEquals(0, calls.get())
        }
    }

    @Test
    fun fixedKeyUploadPreservesSignedFieldsAndOwnQueryUsesOnlyOneExactDevice() {
        Fixture().use { f ->
            val keys = MatrixKeyFixture(f.userId, collaborationMatrixDeviceId(f.login.deviceId))
            val ordinary = keys.signed()
            val explicit = keys.signed(false)
            val fallback = keys.signed(true)
            val upload =
                CollaborationMatrixKeyUpload(
                    keys.device,
                    mapOf("signed_curve25519:1" to ordinary, "signed_curve25519:2" to explicit),
                    mapOf("signed_curve25519:3" to fallback),
                )
            val repo = InternalCollaborationMatrixKeyRepository(f.source)
            f.handler = { exchange ->
                assertEquals("POST", exchange.requestMethod)
                assertEquals("/_matrix/client/v3/keys/upload", exchange.requestURI.path)
                assertNull(exchange.requestURI.rawQuery)
                assertEquals(
                    "Bearer fictional-access-token",
                    exchange.requestHeaders.getFirst("Authorization"),
                )
                val body = f.json.readTree(exchange.requestBody.readNBytes(65537))
                assertEquals(
                    setOf("device_keys", "one_time_keys", "fallback_keys"),
                    body.propertyNames().toSet(),
                )
                assertEquals(
                    f.json.valueToTree<tools.jackson.databind.JsonNode>(keys.body()),
                    body.path("device_keys"),
                )
                assertTrue(
                    body
                        .path("one_time_keys")
                        .path("signed_curve25519:1")
                        .path("fallback")
                        .isMissingNode
                )
                assertEquals(
                    false,
                    body
                        .path("one_time_keys")
                        .path("signed_curve25519:2")
                        .path("fallback")
                        .asBoolean(),
                )
                assertEquals(
                    true,
                    body
                        .path("fallback_keys")
                        .path("signed_curve25519:3")
                        .path("fallback")
                        .asBoolean(),
                )
                assertEquals(
                    ordinary.signatures[f.userId]?.values?.single(),
                    body
                        .path("one_time_keys")
                        .path("signed_curve25519:1")
                        .path("signatures")
                        .path(f.userId)
                        .path("ed25519:${keys.deviceId}")
                        .asString(),
                )
                f.respond(exchange, """{"one_time_key_counts":{"signed_curve25519":2}}""")
            }
            val result = repo.upload(upload, "fictional-access-token", f.login.expiresAt)
            assertInstanceOf(Result.Success::class.java, result)
            assertEquals(
                mapOf("signed_curve25519" to 2),
                (result as Result.Success).value.oneTimeKeyCounts,
            )
            f.handler = { exchange ->
                assertEquals("/_matrix/client/v3/keys/query", exchange.requestURI.path)
                assertEquals("POST", exchange.requestMethod)
                val body = f.json.readTree(exchange.requestBody.readNBytes(2048))
                assertEquals(1000, body.path("timeout").asInt())
                assertEquals(setOf(f.userId), body.path("device_keys").propertyNames().toSet())
                assertEquals(
                    listOf(keys.deviceId),
                    body
                        .path("device_keys")
                        .path(f.userId)
                        .iterator()
                        .asSequence()
                        .map { it.asString() }
                        .toList(),
                )
                f.respond(
                    exchange,
                    f.json.writeValueAsString(
                        mapOf(
                            "device_keys" to
                                mapOf(
                                    f.userId to
                                        mapOf(
                                            keys.deviceId to
                                                (keys.body() +
                                                    ("unsigned" to
                                                        mapOf(
                                                            "device_display_name" to
                                                                "ignored fixture label"
                                                        )))
                                        )
                                )
                        )
                    ),
                )
            }
            assertEquals(
                Result.Success(keys.device),
                repo.ownDeviceKeys(
                    f.userId,
                    keys.deviceId,
                    "fictional-access-token",
                    f.login.expiresAt,
                ),
            )
            assertEquals(2, f.requests.get())
        }
    }

    @Test
    fun malformedKeyResponsesAndForeignDevicesAreRejectedWithoutAutomaticRetry() {
        Fixture().use { f ->
            val repo = InternalCollaborationMatrixKeyRepository(f.source)
            val device = collaborationMatrixDeviceId(f.login.deviceId)
            for (body in
                listOf(
                    "{}",
                    "[]",
                    """{"one_time_key_counts":{"signed_curve25519":-1}}""",
                    """{"one_time_key_counts":{"signed_curve25519":1000001}}""",
                    """{"one_time_key_counts":{"signed_curve25519":true}}""",
                    """{"one_time_key_counts":{"unknown":1}}""",
                    """{"one_time_key_counts":{},"one_time_key_counts":{}}""",
                )) {
                f.handler = { f.respond(it, body) }
                assertEquals(
                    "collaboration_provider_invalid_response",
                    (repo.upload(
                            CollaborationMatrixKeyUpload(),
                            "fictional-access-token",
                            f.login.expiresAt,
                        ) as Result.Failed)
                        .failure
                        .code,
                )
            }
            for (body in
                listOf(
                    mapOf(
                        "device_keys" to mapOf("@foreign:proof.invalid" to emptyMap<String, Any>())
                    ),
                    mapOf(
                        "device_keys" to
                            mapOf(f.userId to mapOf("FOREIGN" to emptyMap<String, Any>()))
                    ),
                    mapOf(
                        "device_keys" to emptyMap<String, Any>(),
                        "failures" to mapOf("foreign.invalid" to emptyMap<String, Any>()),
                    ),
                    mapOf(
                        "device_keys" to mapOf(f.userId to mapOf(device to emptyMap<String, Any>()))
                    ),
                )) {
                f.handler = { f.respond(it, f.json.writeValueAsString(body)) }
                assertEquals(
                    "collaboration_provider_invalid_response",
                    (repo.ownDeviceKeys(
                            f.userId,
                            device,
                            "fictional-access-token",
                            f.login.expiresAt,
                        ) as Result.Failed)
                        .failure
                        .code,
                )
            }
            f.handler = { f.respond(it, """{"device_keys":{}}""") }
            assertEquals(
                Result.Success(null),
                repo.ownDeviceKeys(f.userId, device, "fictional-access-token", f.login.expiresAt),
            )
            val count = f.requests.get()
            f.handler = { f.respond(it, """{"error":"private fixture failure"}""", 503) }
            val failure =
                repo.upload(
                    CollaborationMatrixKeyUpload(),
                    "fictional-access-token",
                    f.login.expiresAt,
                ) as Result.Failed
            assertEquals("collaboration_provider_rejected", failure.failure.code)
            assertFalse(failure.toString().contains("private fixture failure"))
            assertEquals(count + 1, f.requests.get())
            val invalid =
                CollaborationMatrixKeyUpload(
                    oneTimeKeys =
                        (0..100).associate {
                            "signed_curve25519:$it" to MatrixKeyFixture(f.userId, device).signed()
                        }
                )
            assertEquals(
                "collaboration_provider_invalid_response",
                (repo.upload(invalid, "fictional-access-token", f.login.expiresAt) as Result.Failed)
                    .failure
                    .code,
            )
            assertEquals(count + 1, f.requests.get())
        }
    }

    @Test
    fun loginSignsBoundedIdentityAndKeepsBearerOnlyInInternalResult() {
        Fixture().use { f ->
            f.handler = { exchange ->
                assertEquals("/_matrix/client/v3/login", exchange.requestURI.path)
                assertEquals("POST", exchange.requestMethod)
                assertNull(exchange.requestURI.rawQuery)
                assertNull(exchange.requestHeaders.getFirst("Authorization"))
                val body = f.json.readTree(exchange.requestBody.readNBytes(8192))
                assertEquals("org.matrix.login.jwt", body.path("type").asString())
                assertEquals(
                    collaborationMatrixDeviceId(f.login.deviceId),
                    body.path("device_id").asString(),
                )
                assertFalse(body.path("refresh_token").asBoolean())
                val jwt = body.path("token").asString().split('.')
                assertEquals(3, jwt.size)
                val decoder = Base64.getUrlDecoder()
                val header = f.json.readTree(decoder.decode(jwt[0]))
                assertEquals("HS256", header.path("alg").asString())
                val claims = f.json.readTree(decoder.decode(jwt[1]))
                assertEquals(f.config.issuer, claims.path("iss").asString())
                assertEquals(f.config.audience, claims.path("aud").asString())
                assertEquals(
                    collaborationMatrixLocalpart(f.login.companyId, f.login.accountId),
                    claims.path("sub").asString(),
                )
                assertEquals(f.login.expiresAt.epochSecond, claims.path("exp").asLong())
                assertEquals(f.login.issuedAt.epochSecond, claims.path("nbf").asLong())
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(f.config.jwtSecret.toByteArray(), "HmacSHA256"))
                assertArrayEquals(
                    mac.doFinal("${jwt[0]}.${jwt[1]}".toByteArray()),
                    decoder.decode(jwt[2]),
                )
                f.respond(exchange, f.success)
            }
            val result = (f.repository.login(f.login) as Result.Success).value
            assertEquals(f.userId, result.userId)
            assertEquals(collaborationMatrixDeviceId(f.login.deviceId), result.deviceId)
            assertEquals("fictional-access-token", result.accessToken)
            assertFalse(result.toString().contains(result.accessToken))
            assertFalse(f.config.toString().contains(f.config.jwtSecret))
            assertEquals(1, f.requests.get())
        }
    }

    @Test
    fun expiredAndFutureObservationsNeverReachProviderDespiteSynapseClockTolerance() {
        Fixture().use { f ->
            for (request in
                listOf(
                    f.login.copy(expiresAt = f.clock.now),
                    f.login.copy(issuedAt = f.clock.now.plusSeconds(1)),
                    f.login.copy(expiresAt = f.clock.now.plusNanos(1)),
                )) {
                assertEquals(
                    "collaboration_admission_changed",
                    (f.repository.login(request) as Result.Failed).failure.code,
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                HmacMatrixJwtDataSource(f.config)
                    .sign(
                        collaborationMatrixLocalpart(f.login.companyId, f.login.accountId),
                        f.clock.now,
                        f.clock.now.plusSeconds(61),
                    )
            }
            assertEquals(0, f.requests.get())
        }
    }

    @Test
    fun lateSuccessRetainsTheCandidateAndExpiryWithoutDeletingDevice() {
        Fixture().use { f ->
            f.handler = { exchange ->
                f.clock.now = f.login.expiresAt.plusSeconds(1)
                f.respond(exchange, f.success)
            }
            val issued = (f.repository.login(f.login) as Result.Success).value
            assertEquals(f.login.issuedAt.plusSeconds(600), issued.expiresAt)
            assertEquals(1, f.requests.get())
        }
    }

    @Test
    fun permanentDeviceLogoutTreatsOnlyUnknownTokenAsAlreadyCompleteAndDoesNotRetry() {
        Fixture().use { f ->
            for ((status, body, expected) in
                listOf(
                    Triple(401, """{"errcode":"M_UNKNOWN_TOKEN"}""", null),
                    Triple(401, """{"errcode":"M_FORBIDDEN"}""", "collaboration_provider_rejected"),
                    Triple(
                        429,
                        """{"error":"private fixture message"}""",
                        "collaboration_provider_busy",
                    ),
                    Triple(503, "{}", "collaboration_provider_rejected"),
                )) {
                f.handler = {
                    assertEquals("/_matrix/client/v3/logout", it.requestURI.path)
                    assertEquals(
                        "Bearer fictional-access-token",
                        it.requestHeaders.getFirst("Authorization"),
                    )
                    assertNull(it.requestURI.rawQuery)
                    f.respond(it, body, status)
                }
                val result = f.repository.logoutDevice("fictional-access-token")
                if (expected == null) assertEquals(Result.Success(Unit), result)
                else assertEquals(expected, (result as Result.Failed).failure.code)
            }
            assertEquals(4, f.requests.get())
        }
    }

    @Test
    fun strictResponseParsingRejectsAmbiguousBodiesEncodingAndUnboundedChunkedData() {
        Fixture().use { f ->
            val invalid =
                listOf(
                    "[]",
                    "{}",
                    f.success + "{}",
                    f.success.dropLast(1) + ",\"access_token\":\"second\"}",
                    f.success.replace("fictional-access-token", "x".repeat(2049)),
                    f.success.replace("fictional-access-token", "bad\\nheader"),
                    " ".repeat(65537),
                    f.success.replace("600000", "600001"),
                    f.success.replace("600000", "0"),
                    f.success.replace("600000", "1.5"),
                    f.success.dropLast(1) + ",\"refresh_token\":\"unexpected-refresh\"}",
                )
            for (body in invalid) {
                f.handler = { f.respond(it, body) }
                assertEquals(
                    "collaboration_provider_invalid_response",
                    (f.repository.login(f.login) as Result.Failed).failure.code,
                )
            }
            for ((key, value) in
                listOf("Content-Encoding" to "gzip", "Content-Type" to "text/html")) {
                f.handler = { f.respond(it, f.success, headers = mapOf(key to value)) }
                assertEquals(
                    "collaboration_provider_invalid_response",
                    (f.repository.login(f.login) as Result.Failed).failure.code,
                )
            }
            assertEquals(invalid.size + 2, f.requests.get())
        }
    }

    @Test
    fun redirectsCannotChooseAnotherProviderOriginOrCarryItsCredentials() {
        Fixture().use { f ->
            f.handler = { f.respond(it, "{}", 307, mapOf("Location" to "/stolen")) }
            assertEquals(
                "collaboration_provider_rejected",
                (f.repository.login(f.login) as Result.Failed).failure.code,
            )
            assertEquals(1, f.requests.get())
            assertThrows(IllegalArgumentException::class.java) {
                f.configuration(URI("https://provider.example.test/?next=elsewhere"))
            }
            assertThrows(IllegalArgumentException::class.java) {
                f.configuration(URI("https://user:secret@provider.example.test/"))
            }
            assertThrows(IllegalArgumentException::class.java) {
                f.configuration(URI("https://provider.example.test/nested"))
            }
        }
    }

    @Test
    fun bodyDeadlineAndInterruptionCancelTheirOwnedRequests() {
        Fixture(Duration.ofMillis(250)).use { f ->
            val release = CountDownLatch(1)
            try {
                f.handler = { exchange ->
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, 0)
                    exchange.responseBody.flush()
                    release.await(5, TimeUnit.SECONDS)
                }
                assertTimeoutPreemptively(Duration.ofSeconds(2)) {
                    assertEquals(
                        "collaboration_provider_unavailable",
                        (f.repository.login(f.login) as Result.Failed).failure.code,
                    )
                }
            } finally {
                release.countDown()
            }
        }
        Fixture().use { f ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val finished = CountDownLatch(1)
            val cancelled = AtomicBoolean()
            val owner = Executors.newSingleThreadExecutor()
            try {
                f.handler = { exchange ->
                    exchange.responseHeaders.set("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, 0)
                    exchange.responseBody.flush()
                    entered.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
                val pending =
                    owner.submit {
                        try {
                            f.repository.login(f.login)
                        } catch (_: InterruptedException) {
                            cancelled.set(true)
                        } finally {
                            finished.countDown()
                        }
                    }
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                pending.cancel(true)
                assertTrue(finished.await(2, TimeUnit.SECONDS))
                assertTrue(cancelled.get())
                assertEquals(1, f.requests.get())
            } finally {
                release.countDown()
                owner.shutdownNow()
                assertTrue(owner.awaitTermination(5, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun bodySizeFailureCancelsUpstreamAndWrappedCancellationIsPreserved() {
        val cancelled = AtomicBoolean()
        val subscriber = BoundedMatrixBodySubscriber()
        subscriber.onSubscribe(
            object : Flow.Subscription {
                override fun request(n: Long) {}

                override fun cancel() {
                    cancelled.set(true)
                }
            }
        )
        subscriber.onNext(listOf(ByteBuffer.allocate(65536), ByteBuffer.allocate(1)))
        subscriber.onComplete()
        subscriber.onNext(listOf(ByteBuffer.allocate(1)))
        assertTrue(cancelled.get())
        assertTrue(subscriber.body.toCompletableFuture().isCompletedExceptionally)
        assertThrows(CancellationException::class.java) {
            safeMatrixProviderCall<Unit> {
                throw CompletionException(ExecutionException(CancellationException()))
            }
        }
    }

    private class MutableClock : Clock() {
        @Volatile var now = Instant.parse("2026-10-31T12:00:00Z")

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }

    @Test
    fun identityUsesOnlyFixedGetAndStrictBoundedResponseWithoutForwardingProviderFields() {
        Fixture().use { f ->
            f.handler = { exchange ->
                assertEquals("/_matrix/client/v3/account/whoami", exchange.requestURI.path)
                assertEquals("GET", exchange.requestMethod)
                assertNull(exchange.requestURI.rawQuery)
                assertEquals(
                    "Bearer fictional-private-token",
                    exchange.requestHeaders.getFirst("Authorization"),
                )
                assertEquals(0, exchange.requestBody.readNBytes(1).size)
                f.respond(
                    exchange,
                    """{"user_id":"@fixture:matrix.example.test","device_id":"DEVICE","is_guest":false,"ignored":"private provider field"}""",
                )
            }
            val result =
                (f.repository.whoAmI("fictional-private-token", f.clock.now.plusSeconds(20))
                        as Result.Success)
                    .value
            assertEquals("@fixture:matrix.example.test", result.userId)
            assertEquals("DEVICE", result.deviceId)
            assertFalse(result.isGuest)
            assertFalse(result.toString().contains("private"))
            for (body in
                listOf(
                    "{}",
                    "[]",
                    """{"user_id":"@x:test","device_id":"DEVICE","is_guest":"false"}""",
                    """{"user_id":"@x:test","device_id":"DEVICE","is_guest":false,"is_guest":true}""",
                )) {
                f.handler = { f.respond(it, body) }
                assertEquals(
                    "collaboration_provider_invalid_response",
                    (f.repository.whoAmI("fictional-private-token", f.clock.now.plusSeconds(20))
                            as Result.Failed)
                        .failure
                        .code,
                )
            }
            val count = f.requests.get()
            assertEquals(
                "collaboration_admission_changed",
                (f.repository.whoAmI("fictional-private-token", f.clock.now) as Result.Failed)
                    .failure
                    .code,
            )
            assertEquals(count, f.requests.get())
        }
    }

    @Test
    fun boundedProviderCapacityRejectsWithoutQueueingAndInterruptionReleasesPermit() {
        Fixture(maximumConcurrent = 1).use { f ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val stopped = CountDownLatch(1)
            val caller = Executors.newSingleThreadExecutor()
            f.handler = {
                entered.countDown()
                assertTrue(release.await(5, TimeUnit.SECONDS))
                f.respond(
                    it,
                    """{"user_id":"@fixture:test","device_id":"DEVICE","is_guest":false}""",
                )
            }
            try {
                val running =
                    caller.submit {
                        try {
                            f.repository.whoAmI("fictional-token", f.clock.now.plusSeconds(20))
                        } finally {
                            stopped.countDown()
                        }
                    }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val refused =
                    f.repository.whoAmI("fictional-token", f.clock.now.plusSeconds(20))
                        as Result.Failed
                assertEquals(
                    dev.fajar.hris.core.domain.FailureKind.RATE_LIMITED,
                    refused.failure.kind,
                )
                val refusedRoom =
                    f.rooms.resolve(f.roomRequest, "fictional-token", f.clock.now.plusSeconds(20))
                        as Result.Failed
                assertEquals(
                    dev.fajar.hris.core.domain.FailureKind.RATE_LIMITED,
                    refusedRoom.failure.kind,
                )
                assertEquals(1, f.requests.get())
                running.cancel(true)
                assertTrue(stopped.await(5, TimeUnit.SECONDS))
                release.countDown()
                assertInstanceOf(
                    Result.Success::class.java,
                    f.repository.whoAmI("fictional-token", f.clock.now.plusSeconds(20)),
                )
                assertEquals(2, f.requests.get())
            } finally {
                release.countDown()
                caller.shutdownNow()
                assertTrue(caller.awaitTermination(5, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun roomAliasResolutionUsesOnlyEncodedFixedOriginAndRecognizesExactProviderOutcomes() {
        Fixture().use { f ->
            f.handler = { exchange ->
                assertEquals("GET", exchange.requestMethod)
                assertTrue(
                    exchange.requestURI.rawPath.startsWith(
                        "/_matrix/client/v3/directory/room/%23hris_"
                    )
                )
                assertNull(exchange.requestURI.rawQuery)
                assertEquals(
                    "Bearer fictional-room-token",
                    exchange.requestHeaders.getFirst("Authorization"),
                )
                f.respond(exchange, """{"errcode":"M_NOT_FOUND"}""", 404)
            }
            assertEquals(
                Result.Success(null),
                f.rooms.resolve(f.roomRequest, "fictional-room-token", f.clock.now.plusSeconds(20)),
            )
            for ((status, body) in
                listOf(
                    404 to """{"errcode":"OTHER"}""",
                    403 to """{"errcode":"M_NOT_FOUND"}""",
                    200 to """{"room_id":"not-a-room"}""",
                )) {
                f.handler = { f.respond(it, body, status) }
                assertInstanceOf(
                    Result.Failed::class.java,
                    f.rooms.resolve(
                        f.roomRequest,
                        "fictional-room-token",
                        f.clock.now.plusSeconds(20),
                    ),
                )
            }
            f.handler = { f.respond(it, """{"errcode":"M_ROOM_IN_USE"}""", 400) }
            assertEquals(
                Result.Success(CollaborationMatrixRoomCreation.AliasInUse),
                f.rooms.create(f.roomRequest, "fictional-room-token", f.clock.now.plusSeconds(20)),
            )
            f.handler = { f.respond(it, """{"room_id":"!hash-without-server"}""") }
            assertEquals(
                Result.Success(CollaborationMatrixRoomCreation.Created("!hash-without-server")),
                f.rooms.create(f.roomRequest, "fictional-room-token", f.clock.now.plusSeconds(20)),
            )
        }
    }

    @Test
    fun roomStateCannotSelectAUrlAndRejectsAmbiguousOrOversizedProviderArrays() {
        Fixture().use { f ->
            f.handler = { exchange ->
                assertEquals("GET", exchange.requestMethod)
                assertNull(exchange.requestURI.rawQuery)
                assertNull(exchange.requestURI.rawFragment)
                assertEquals(
                    "/_matrix/client/v3/rooms/%21room%2F..%2F%3Ftoken%3Dforeign%23fragment/state",
                    exchange.requestURI.rawPath,
                )
                f.respond(exchange, "[]")
            }
            assertInstanceOf(
                Result.Success::class.java,
                f.rooms.observe(
                    "!room/../?token=foreign#fragment",
                    "fictional-room-token",
                    f.clock.now.plusSeconds(20),
                ),
            )
            val event =
                """{"type":"m.room.encryption","state_key":"","sender":"@creator:test","content":{"algorithm":"m.megolm.v1.aes-sha2"}}"""
            val malformed =
                listOf(
                    "{}",
                    "[{}]",
                    "[$event,$event]",
                    "[] {}",
                    """[{"type":"m.room.encryption","type":"m.room.create","state_key":"","sender":"@creator:test","content":{}}]""",
                    """[{"type":"m.room.encryption","state_key":"","sender":"@creator:test","content":{}}]""",
                    "[" + List(257) { event }.joinToString(",") + "]",
                    "[" + " ".repeat(65537) + "]",
                )
            for (body in malformed) {
                f.handler = { f.respond(it, body) }
                assertEquals(
                    "collaboration_provider_invalid_response",
                    (f.rooms.observe("!room", "fictional-room-token", f.clock.now.plusSeconds(20))
                            as Result.Failed)
                        .failure
                        .code,
                )
            }
            val count = f.requests.get()
            assertEquals(
                "collaboration_admission_changed",
                (f.rooms.observe("!room", "fictional-room-token", f.clock.now) as Result.Failed)
                    .failure
                    .code,
            )
            assertEquals(count, f.requests.get())
        }
    }

    @Test
    fun roomMutationsHaveBoundedTypedPathsAndStrictAcknowledgements() {
        Fixture().use { f ->
            f.handler = { exchange ->
                assertEquals("PUT", exchange.requestMethod)
                assertEquals(
                    "/_matrix/client/v3/rooms/%21room/state/m.room.encryption",
                    exchange.requestURI.rawPath,
                )
                assertEquals(
                    "m.megolm.v1.aes-sha2",
                    f.json.readTree(exchange.requestBody.readNBytes(4096))["algorithm"].asString(),
                )
                f.respond(
                    exchange,
                    f.json.writeValueAsString(mapOf("event_id" to "\$fixture-event")),
                )
            }
            assertEquals(
                Result.Success(Unit),
                f.rooms.putInitialSetting(
                    "!room",
                    CollaborationMatrixRoomSetting.ENCRYPTION,
                    "fictional-room-token",
                    f.clock.now.plusSeconds(20),
                ),
            )
            f.handler = { f.respond(it, """{"event_id":"invalid"}""") }
            assertInstanceOf(
                Result.Failed::class.java,
                f.rooms.putInitialSetting(
                    "!room",
                    CollaborationMatrixRoomSetting.ENCRYPTION,
                    "fictional-room-token",
                    f.clock.now.plusSeconds(20),
                ),
            )
            f.handler = { exchange ->
                assertEquals("POST", exchange.requestMethod)
                assertEquals("/_matrix/client/v3/rooms/%21room/invite", exchange.requestURI.rawPath)
                val body = f.json.readTree(exchange.requestBody.readNBytes(4096))
                assertEquals(setOf("user_id"), body.propertyNames().toSet())
                f.respond(exchange, "{}")
            }
            assertEquals(
                Result.Success(Unit),
                f.rooms.invitePeer(
                    "!room",
                    f.roomRequest,
                    "fictional-room-token",
                    f.clock.now.plusSeconds(20),
                ),
            )
        }
    }

    private class Fixture(timeout: Duration = Duration.ofSeconds(5), maximumConcurrent: Int = 32) :
        AutoCloseable {
        val json = JsonMapper.builder().build()
        val clock = MutableClock()
        val requests = AtomicInteger()
        val login =
            CollaborationMatrixLogin(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                clock.now,
                clock.now.plusSeconds(60),
            )
        val userId =
            "@${collaborationMatrixLocalpart(login.companyId, login.accountId)}:matrix.example.test"
        val success =
            json.writeValueAsString(
                mapOf(
                    "user_id" to userId,
                    "device_id" to collaborationMatrixDeviceId(login.deviceId),
                    "access_token" to "fictional-access-token",
                    "expires_in_ms" to 600000,
                )
            )
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newFixedThreadPool(2)
        val client =
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
        val config = configuration(URI("http://127.0.0.1:${server.address.port}"), timeout)
        val source = HttpMatrixClientDataSource(client, config, clock, maximumConcurrent)
        val rooms = InternalCollaborationMatrixRoomRepository(source, config.serverName)
        val roomRequest =
            CollaborationMatrixDirectRoom(
                login.companyId,
                UUID.randomUUID(),
                login.accountId,
                UUID.randomUUID(),
            )
        val repository =
            InternalCollaborationMatrixRepository(HmacMatrixJwtDataSource(config), source, clock)
        @Volatile var handler: (HttpExchange) -> Unit = { respond(it, success) }
        private val serverErrors = ConcurrentLinkedQueue<Throwable>()

        init {
            server.executor = executor
            server.createContext("/") { exchange ->
                requests.incrementAndGet()
                try {
                    handler(exchange)
                } catch (_: java.io.IOException) {
                    /* Client cancellation owns the exchange. */
                } catch (error: Throwable) {
                    serverErrors.add(error)
                } finally {
                    exchange.close()
                }
            }
            server.start()
        }

        fun configuration(origin: URI, timeout: Duration = Duration.ofSeconds(5)) =
            MatrixProviderConfiguration(
                origin,
                "matrix.example.test",
                "urn:hris:matrix:fixture",
                "matrix:fixture",
                "fictional-matrix-secret-32-characters-long",
                timeout,
            )

        fun respond(
            exchange: HttpExchange,
            body: String,
            status: Int = 200,
            headers: Map<String, String> = emptyMap(),
        ) {
            exchange.responseHeaders.set("Content-Type", "application/json")
            headers.forEach { (key, value) -> exchange.responseHeaders.set(key, value) }
            exchange.sendResponseHeaders(status, 0)
            exchange.responseBody.write(body.toByteArray())
        }

        override fun close() {
            client.shutdownNow()
            server.stop(0)
            // The test has released every handler latch. Let those handlers observe release
            // before interrupting anything, or cleanup itself fabricates a server assertion.
            executor.shutdown()
            try {
                assertTrue(client.awaitTermination(Duration.ofSeconds(5)))
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
            } finally {
                if (!executor.isTerminated) executor.shutdownNow()
            }
            serverErrors.firstOrNull()?.let {
                throw AssertionError("Fixture server assertion failed", it)
            }
        }
    }
}
