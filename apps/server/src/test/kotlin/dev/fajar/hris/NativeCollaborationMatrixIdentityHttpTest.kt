package dev.fajar.hris

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.*
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
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
    private val ownedParents = mutableSetOf<UUID>()

    @BeforeEach
    fun alignProtocolClockWithCurrentDatabaseTime() {
        // This live eligibility view uses database time. The broader People fixture
        // intentionally uses October 1; that is not a live protocol parent today.
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS))
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

    private fun publishedRoom(n: Native, device: UUID): String {
        // Owned SQL/protocol fixture; the actual publication worker has separate coverage.
        val db = database()
        val peer = UUID.randomUUID()
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
                "messages?dir=b&filter=%7B%7D",
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
        val response = sync(n, token, "?timeout=0")
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
        val cursor = json.readTree(response.body()).path("next_batch").asString()
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
        val keyUploads = AtomicInteger()
        val keyQueries = AtomicInteger()
        val keyClaims = AtomicInteger()
        val encryptedSends = AtomicInteger()
        val onEncryptedSend = AtomicReference<(() -> Unit)?>(null)
        val lastEnvelope = AtomicReference<tools.jackson.databind.JsonNode>()
        val onKeyClaim = AtomicReference<(() -> Unit)?>(null)
        private val claimable =
            ConcurrentHashMap<Pair<String, String>, tools.jackson.databind.JsonNode>()
        val onKeyQuery = AtomicReference<(() -> Unit)?>(null)
        val onKeyUpload = AtomicReference<(() -> Unit)?>(null)
        private val keys =
            ConcurrentHashMap<Pair<String, String>, tools.jackson.databind.JsonNode>()
        private val keyIds = ConcurrentHashMap<Pair<String, String>, MutableSet<String>>()
        val logins = AtomicInteger()
        val queries = AtomicInteger()
        val syncs = AtomicInteger()
        val onSync = AtomicReference<(() -> Unit)?>(null)
        val roomStates = ConcurrentHashMap<String, List<Map<String, Any>>>()
        val roomCalls = AtomicInteger()
        val lastMemberQuery = AtomicReference<String>()
        val lastHistoryQuery = AtomicReference<String>()
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
                                                operation.startsWith("context/") ->
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
                                                        "state" to events,
                                                        "start" to "private-history-1",
                                                        "end" to "private-history-2",
                                                    )
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
