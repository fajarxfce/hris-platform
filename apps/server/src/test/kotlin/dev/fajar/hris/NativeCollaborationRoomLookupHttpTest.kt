package dev.fajar.hris

import java.net.URI
import java.net.URLEncoder
import java.net.http.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
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
class NativeCollaborationRoomLookupHttpTest : NativeGatewaySdkFixture() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun matrix(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_MATRIX_JWT_SECRET_FILE") {
                Path.of(
                        requireNotNull(
                                NativeCollaborationRoomLookupHttpTest::class
                                    .java
                                    .getResource("/matrix-signing.fixture")
                            )
                            .toURI()
                    )
                    .toString()
            }
        }
    }

    @Test
    fun roomHintResolvesOnlyOwnedSummaryWithEncodedIdentifiersAndNoProviderIo() {
        val n = native()
        val peer = sdkPeer(n)
        val registered = register(n)
        assertEquals(200, registered.statusCode())
        val device = UUID.fromString(json.readTree(registered.body())["id"].asString())
        database()
            .update(
                "update collaboration_devices set state='ACTIVE',version=version+1,updated_at=now() where id=?",
                device,
            )
        val conversation = UUID.randomUUID()
        val room = "!fixture/+?=:matrix.example.test"
        publishSdkRoom(n, device, peer.fixture.account, room, conversation)
        val result = lookup(n, room)
        assertEquals(200, result.statusCode(), result.body())
        assertEquals("no-store", result.headers().firstValue("Cache-Control").orElse(""))
        assertTrue(result.headers().firstValue("Set-Cookie").isEmpty)
        val summary = json.readTree(result.body())
        assertEquals(setOf("conversation", "peer"), summary.propertyNames().toSet())
        assertEquals(conversation.toString(), summary["conversation"]["id"].asString())
        assertEquals(peer.fixture.account.toString(), summary["peer"]["accountId"].asString())
        assertFalse(result.body().contains(room))
        val opposite = lookup(peer, room)
        assertEquals(200, opposite.statusCode(), opposite.body())
        assertEquals(
            n.fixture.account.toString(),
            json.readTree(opposite.body())["peer"]["accountId"].asString(),
        )
        failure(lookup(sdkPeer(n), room), 404, "collaboration_conversation_not_found")
        failure(lookup(native(), room), 404, "collaboration_conversation_not_found")
        failure(
            lookup(n, "!unknown:matrix.example.test"),
            404,
            "collaboration_conversation_not_found",
        )
        database().update("update accounts set active=false where id=?", peer.fixture.account)
        assertEquals(200, lookup(n, room).statusCode())
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from background_jobs where company_id=?",
                    Int::class.java,
                    n.fixture.company,
                ),
        )
    }

    @Test
    fun nativeTransportClientPolicyAndRevocationRemainAuthoritativeForLookup() {
        val n = native()
        assertEquals(401, lookup(n, "!unknown", token = null).statusCode())
        failure(get(n.fixture.browser, path(n, "!unknown")), 403, "invalid_session_transport")
        assertEquals(400, lookup(n, null).statusCode())
        for (invalid in listOf("", "!", "alias", "!line\n", "!" + "x".repeat(255))) {
            failure(lookup(n, invalid), 422, "invalid_collaboration_room_request")
        }
        failure(lookup(n, "!unknown", platform = "WEB"), 422, "invalid_client_version")
        assertEquals(
            200,
            save(n.fixture, changes = mapOf("disabledModules" to listOf("COLLABORATION")))
                .statusCode(),
        )
        failure(lookup(n, "!unknown"), 403, "company_module_disabled")
        database()
            .update(
                "update native_sessions set revoked_at=now(),version=version+1 where id=?",
                n.session,
            )
        failure(lookup(n, "!unknown"), 401, "native_session_invalid")
    }

    @Test
    fun transportReturnsCurrentDeviceAndPublishedRoomInOneUncachedNativeRead() {
        val n = native()
        val peer = sdkPeer(n)
        val registered = register(n)
        assertEquals(200, registered.statusCode())
        val device = UUID.fromString(json.readTree(registered.body())["id"].asString())
        database()
            .update(
                "update collaboration_devices set state='ACTIVE',version=version+1,updated_at=now() where id=?",
                device,
            )
        val conversation = UUID.randomUUID()
        val room = "!transport:matrix.example.test"
        publishSdkRoom(n, device, peer.fixture.account, room, conversation)
        val response = transport(n, conversation)
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
        assertTrue(response.headers().firstValue("Set-Cookie").isEmpty)
        val value = json.readTree(response.body())
        assertEquals(
            setOf(
                "companyId",
                "accountId",
                "nativeSessionId",
                "deviceId",
                "deviceVersion",
                "platform",
                "conversationId",
                "firstAccountId",
                "secondAccountId",
                "matrixRoomId",
                "roomProvisioningVersion",
                "recipientReady",
            ),
            value.propertyNames().toSet(),
        )
        assertEquals(n.fixture.company.toString(), value["companyId"].asString())
        assertEquals(n.fixture.account.toString(), value["accountId"].asString())
        assertEquals(n.session.toString(), value["nativeSessionId"].asString())
        assertEquals(device.toString(), value["deviceId"].asString())
        assertEquals(1, value["deviceVersion"].asInt())
        assertEquals("ANDROID", value["platform"].asString())
        assertEquals(conversation.toString(), value["conversationId"].asString())
        assertEquals(room, value["matrixRoomId"].asString())
        assertFalse(value["recipientReady"].asBoolean())
        failure(transport(sdkPeer(n), conversation), 404, "collaboration_conversation_not_found")
        failure(transport(native(), conversation), 404, "collaboration_conversation_not_found")
        failure(transport(peer, conversation), 503, "collaboration_device_unavailable")
        val peerRegistration = register(peer)
        assertEquals(200, peerRegistration.statusCode())
        val peerDevice = UUID.fromString(json.readTree(peerRegistration.body())["id"].asString())
        database()
            .update(
                "update collaboration_devices set state='ACTIVE',version=version+1,updated_at=now() where id=?",
                peerDevice,
            )
        database()
            .update(
                "insert into collaboration_device_key_publications(company_id,device_id,account_id,fingerprint,version,reserved_at) values(?,?,?,?,0,now())",
                n.fixture.company,
                peerDevice,
                peer.fixture.account,
                "a".repeat(64),
            )
        assertFalse(json.readTree(transport(n, conversation).body())["recipientReady"].asBoolean())
        database()
            .update(
                "update collaboration_device_key_publications set version=1,published_at=now() where device_id=?",
                peerDevice,
            )
        database()
            .update(
                "update native_sessions set access_expires_at=now()-interval '1 hour',mfa_verified_at=null where id=?",
                peer.session,
            )
        assertTrue(json.readTree(transport(n, conversation).body())["recipientReady"].asBoolean())
        database().update("update accounts set active=false where id=?", peer.fixture.account)
        val departed = transport(n, conversation)
        assertEquals(200, departed.statusCode())
        assertFalse(json.readTree(departed.body())["recipientReady"].asBoolean())
        database()
            .update(
                "update collaboration_devices set state='REVOKED',revoked_at=now(),version=version+1,updated_at=now() where id=?",
                device,
            )
        failure(transport(n, conversation), 503, "collaboration_device_unavailable")
    }

    @Test
    fun transportPreservesNativeTransportPolicyAndSessionDenials() {
        val n = native()
        val conversation = UUID.randomUUID()
        assertEquals(401, transport(n, conversation, token = null).statusCode())
        failure(
            get(
                n.fixture.browser,
                "${n.fixture.root}/collaboration/direct-conversations/$conversation/transport",
            ),
            403,
            "invalid_session_transport",
        )
        failure(transport(n, conversation, platform = "WEB"), 422, "invalid_client_version")
        assertEquals(
            200,
            save(n.fixture, changes = mapOf("disabledModules" to listOf("COLLABORATION")))
                .statusCode(),
        )
        failure(transport(n, conversation), 403, "company_module_disabled")
        database()
            .update(
                "update native_sessions set revoked_at=now(),version=version+1 where id=?",
                n.session,
            )
        failure(transport(n, conversation), 401, "native_session_invalid")
    }

    private fun transport(
        n: Native,
        conversation: UUID,
        token: String? = n.access,
        platform: String = "ANDROID",
    ): HttpResponse<String> {
        val path = "${n.fixture.root}/collaboration/direct-conversations/$conversation/transport"
        val builder =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                .timeout(Duration.ofSeconds(15))
                .header("X-HRIS-Client-Platform", platform)
                .header("X-HRIS-Client-Build", "1")
        token?.let { builder.header("Authorization", "Bearer $it") }
        return if (token == null)
            HttpClient.newHttpClient().use {
                it.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
            }
        else n.fixture.browser.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun path(n: Native, room: String?) =
        "${n.fixture.root}/collaboration/direct-conversations/summary-by-room" +
            (room?.let { "?roomId=" + URLEncoder.encode(it, UTF_8) } ?: "")

    private fun lookup(
        n: Native,
        room: String?,
        token: String? = n.access,
        platform: String = "ANDROID",
    ): HttpResponse<String> {
        val builder =
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port${path(n, room)}"))
                .timeout(Duration.ofSeconds(15))
                .header("X-HRIS-Client-Platform", platform)
                .header("X-HRIS-Client-Build", "1")
        token?.let { builder.header("Authorization", "Bearer $it") }
        return if (token == null)
            HttpClient.newHttpClient().use {
                it.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
            }
        else n.fixture.browser.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
    }
}
