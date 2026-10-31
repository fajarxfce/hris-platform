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

    @Test
    fun directOpenListAndDetailRequireNativeAuthorityAndStableOperations() {
        val n = native()
        val peer = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional direct peer')",
                peer,
                "$peer@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                n.fixture.company,
                peer,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                n.fixture.company,
                peer,
            )
        val path = "${n.fixture.root}/collaboration/direct-conversations"
        fun request(
            suffix: String = "",
            body: Map<String, Any>? = null,
            operation: UUID? = null,
            build: String = "1",
        ): HttpResponse<String> {
            val builder =
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path$suffix"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer ${n.access}")
                    .header("X-HRIS-Client-Platform", "ANDROID")
                    .header("X-HRIS-Client-Build", build)
            operation?.let { builder.header("Idempotency-Key", it.toString()) }
            if (body == null) builder.GET()
            else
                builder
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            return n.fixture.browser.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        }
        val body = mapOf("peerAccountId" to peer)
        val operation = UUID.randomUUID()
        assertEquals(400, request(body = body).statusCode())
        assertEquals(
            400,
            request(body = body + ("companyId" to UUID.randomUUID()), operation = operation)
                .statusCode(),
        )
        failure(
            request(body = mapOf("peerAccountId" to n.fixture.account), operation = operation),
            422,
            "invalid_collaboration_peer",
        )
        val created = request(body = body, operation = operation)
        assertEquals(200, created.statusCode(), created.body())
        assertEquals("no-store", created.headers().firstValue("Cache-Control").orElse(""))
        val id = json.readTree(created.body())["id"].asString()
        val replay = request(body = body, operation = operation)
        assertEquals(200, replay.statusCode(), replay.body())
        assertEquals(json.readTree(created.body()), json.readTree(replay.body()))
        assertEquals(id, json.readTree(replay.body())["id"].asString())
        val detail = request("/$id")
        assertEquals(200, detail.statusCode(), detail.body())
        val head = json.readTree(detail.body())
        assertEquals(
            setOf(
                "id",
                "companyId",
                "firstAccountId",
                "secondAccountId",
                "createdBy",
                "createdAt",
                "version",
            ),
            head.propertyNames().toSet(),
        )
        assertEquals(
            setOf(peer.toString(), n.fixture.account.toString()),
            setOf(head["firstAccountId"].asString(), head["secondAccountId"].asString()),
        )
        assertFalse(detail.body().contains("example.test"))
        val page = request("?limit=1")
        assertEquals(200, page.statusCode(), page.body())
        assertEquals(id, json.readTree(page.body())["items"][0]["id"].asString())
        assertEquals("no-store", page.headers().firstValue("Cache-Control").orElse(""))
        failure(request("/${UUID.randomUUID()}"), 404, "collaboration_conversation_not_found")
        failure(request("?limit=51"), 422, "invalid_collaboration_conversation_query")
        assertEquals(400, request("?after=invalid").statusCode())
        failure(get(n.fixture.browser, path), 403, "invalid_session_transport")
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_devices where company_id=?",
                    Int::class.java,
                    n.fixture.company,
                ),
        )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                n.fixture.company,
                peer,
            )
        failure(request(body = body, operation = operation), 403, "collaboration_peer_unavailable")
        assertEquals(200, request("/$id").statusCode())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                n.fixture.company,
                n.fixture.account,
            )
        assertEquals(403, request("/$id").statusCode())
    }

    @Test
    fun directRoomSetupAndStatusUseNativeObservedCommandsWithoutCallingProvider() {
        val n = native()
        val peer = UUID.randomUUID()
        val db = database()
        db.update(
            "insert into accounts(id,email,display_name) values(?,?,'Fictional room HTTP peer')",
            peer,
            "$peer@example.test",
        )
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
        val enrolled = register(n)
        assertEquals(200, enrolled.statusCode(), enrolled.body())
        val device = UUID.fromString(json.readTree(enrolled.body())["id"].asString())
        val root = "${n.fixture.root}/collaboration/direct-conversations"
        fun request(
            path: String,
            body: Map<String, Any?>? = null,
            operation: UUID? = null,
            build: String = "1",
        ): HttpResponse<String> {
            val builder =
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer ${n.access}")
                    .header("X-HRIS-Client-Platform", "ANDROID")
                    .header("X-HRIS-Client-Build", build)
            operation?.let { builder.header("Idempotency-Key", it.toString()) }
            if (body == null) builder.GET()
            else
                builder
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            return n.fixture.browser.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        }
        val opened = request(root, mapOf("peerAccountId" to peer), UUID.randomUUID())
        assertEquals(200, opened.statusCode(), opened.body())
        val id = json.readTree(opened.body())["id"].asString()
        val path = "$root/$id/room"
        val empty = request(path)
        assertEquals(200, empty.statusCode(), empty.body())
        assertEquals("NOT_REQUESTED", json.readTree(empty.body())["state"].asString())
        val body =
            mapOf(
                "deviceId" to device,
                "expectedDeviceVersion" to 1,
                "expectedProvisioningVersion" to null,
            )
        val operation = UUID.randomUUID()
        assertEquals(400, request(path, body).statusCode())
        assertEquals(
            400,
            request(path, body + ("matrixRoomId" to "!injected"), operation).statusCode(),
        )
        failure(request(path, body, operation), 403, "collaboration_access_denied")
        db.update(
            "update collaboration_devices set state='ACTIVE',version=version+1,updated_at=? where id=?",
            clock.instant().atOffset(java.time.ZoneOffset.UTC),
            device,
        )
        failure(
            request(path, body + ("expectedDeviceVersion" to 0), operation),
            409,
            "collaboration_room_provisioning_changed",
        )
        val queued = request(path, body, operation)
        assertEquals(200, queued.statusCode(), queued.body())
        assertEquals("no-store", queued.headers().firstValue("Cache-Control").orElse(""))
        assertEquals(
            json.readTree(queued.body()),
            json.readTree(request(path, body, operation).body()),
        )
        val status = request(path)
        assertEquals(200, status.statusCode(), status.body())
        assertEquals("no-store", status.headers().firstValue("Cache-Control").orElse(""))
        val value = json.readTree(status.body())
        assertEquals(
            setOf(
                "conversationId",
                "version",
                "state",
                "matrixRoomId",
                "creatorAccountId",
                "retryable",
                "retryAt",
            ),
            value.propertyNames().toSet(),
        )
        assertEquals("QUEUED", value["state"].asString())
        assertTrue(value["matrixRoomId"].isNull)
        assertFalse(value["retryable"].asBoolean())
        failure(get(n.fixture.browser, path), 403, "invalid_session_transport")
        failure(
            request("$root/${UUID.randomUUID()}/room"),
            404,
            "collaboration_conversation_not_found",
        )
        assertEquals(
            2,
            db.queryForObject(
                "select count(*) from background_jobs where company_id=?",
                Int::class.java,
                n.fixture.company,
            ),
        )
        assertEquals(
            0,
            db.queryForObject(
                "select count(*) from collaboration_provider_sessions where company_id=?",
                Int::class.java,
                n.fixture.company,
            ),
        )
        db.update(
            "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
            n.fixture.company,
            peer,
        )
        failure(request(path, body, operation), 403, "collaboration_peer_unavailable")
        assertEquals(200, request(path).statusCode())
        db.update(
            "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
            n.fixture.company,
            n.fixture.account,
        )
        assertEquals(403, request(path).statusCode())
    }

    @Test
    fun participantsAreNativeOnlyCompanyScopedPublicReferencesWithoutProviderCalls() {
        val n = native()
        val peer = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional chat participant')",
                peer,
                "$peer@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                n.fixture.company,
                peer,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                n.fixture.company,
                peer,
            )
        val path = "${n.fixture.root}/collaboration/participants"
        fun read(suffix: String = "") =
            n.fixture.browser.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path$suffix"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer ${n.access}")
                    .header("X-HRIS-Client-Platform", "ANDROID")
                    .header("X-HRIS-Client-Build", "1")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        val response = read("?query=Fictional&limit=1")
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
        val result = json.readTree(response.body())
        assertEquals(setOf("items", "nextCursor"), result.propertyNames().toSet())
        assertEquals(1, result["items"].size())
        assertEquals(setOf("accountId", "displayName"), result["items"][0].propertyNames().toSet())
        assertEquals(peer.toString(), result["items"][0]["accountId"].asString())
        assertTrue(result["nextCursor"].isNull)
        assertFalse(response.body().contains("example.test"))
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from collaboration_devices where company_id=?",
                    Int::class.java,
                    n.fixture.company,
                ),
        )
        failure(get(n.fixture.browser, path), 403, "invalid_session_transport")
        failure(read("?limit=51"), 422, "invalid_collaboration_participant_query")
        assertEquals(400, read("?after=invalid").statusCode())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                n.fixture.company,
                peer,
            )
        assertEquals(0, json.readTree(read().body())["items"].size())
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='collaboration.chat'",
                n.fixture.company,
                n.fixture.account,
            )
        failure(read(), 403, "access_denied")
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
