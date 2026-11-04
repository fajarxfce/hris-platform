package dev.fajar.hris

import dev.fajar.hris.collaboration.data.matrix.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationMatrixRepository
import dev.fajar.hris.collaboration.domain.repositories.CollaborationProviderSessionRepository
import dev.fajar.hris.core.domain.Actor
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.core.domain.TransactionRunner
import java.net.URI
import java.net.http.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource

/**
 * Actual Synapse -> pinned TLS proxy -> API/RLS/queue. Ciphertext here is a synthetic protocol
 * fixture.
 */
@TestPropertySource(
    properties =
        [
            "HRIS_COLLABORATION_ENABLED=true",
            "HRIS_MATRIX_ENABLED=true",
            "HRIS_COLLABORATION_PUSH_ENABLED=true",
            "HRIS_COLLABORATION_PUSH_CALLBACK_ENABLED=true",
            "HRIS_MATRIX_PUSH_CALLBACK_SOURCES=127.0.0.1/32",
            "HRIS_MATRIX_SERVER_NAME=matrix.example.test",
            "HRIS_MATRIX_JWT_ISSUER=urn:hris:matrix:fixture",
            "HRIS_MATRIX_JWT_AUDIENCE=matrix:fixture",
        ]
)
class NativeCollaborationPushProviderHttpTest : NativeCollaborationApiFixture() {
    companion object {
        private val provider = MatrixPushProviderFixture()

        @JvmStatic
        @DynamicPropertySource
        fun matrix(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_MATRIX_ORIGIN") { provider.origin.toString() }
            registry.add("HRIS_MATRIX_JWT_SECRET_FILE") { provider.signingFile.toString() }
            registry.add("HRIS_MATRIX_PUSH_GATEWAY_URL") { provider.callback }
        }

        @JvmStatic @AfterAll fun stopProvider() = provider.close()
    }

    @Autowired private lateinit var credentials: CollaborationProviderSessionRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var matrix: CollaborationMatrixRepository
    @Autowired private lateinit var configuration: MatrixProviderConfiguration
    private val ownedParents = mutableSetOf<UUID>()

    @BeforeEach
    fun prepareProviderIngress() {
        clock.followSystemTime()
        provider.startProxy(port)
    }

    @AfterEach
    fun retireParents() {
        for (parent in ownedParents) database()
            .update(
                "update native_sessions set revoked_at=coalesce(revoked_at,now()),version=version+1 where id=?",
                parent,
            )
    }

    @Test
    fun actualSynapseRetriesAcrossPrivateTlsOutageAndRemovesOnlyTheRetiredPusher() {
        val unknown = "hriscp1_" + "a".repeat(64)
        val probe =
            """{"notification":{"counts":{"unread":1},"devices":[{"app_id":"dev.fajar.hris.chat","pushkey":"$unknown"}]}}"""
        fun http(origin: URI, body: String = probe): HttpResponse<String> =
            provider.client.send(
                HttpRequest.newBuilder(origin.resolve("/_matrix/push/v1/notify"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(403, http(provider.publicOrigin).statusCode())
        assertEquals(200, http(provider.tlsOrigin).statusCode())
        assertEquals(413, http(provider.tlsOrigin, " ".repeat(8193)).statusCode())
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().use { untrusted ->
            assertThrows(javax.net.ssl.SSLHandshakeException::class.java) {
                untrusted.send(
                    HttpRequest.newBuilder(provider.tlsOrigin.resolve("/untrusted"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.discarding(),
                )
            }
        }
        val n = native()
        val (device, gateway) = enroll(n)
        savePush(n)
        assertEquals(200, pusher(n, gateway).statusCode())
        val actor =
            Actor(
                n.fixture.account,
                n.fixture.company,
                emptySet(),
                clock.instant(),
                UUID.randomUUID(),
            )
        val owner =
            requireNotNull(
                success(
                    transactions.run(actor) {
                        credentials.find(n.fixture.company, n.fixture.account, device)
                    }
                )
            )
        val peerAccount = UUID.randomUUID()
        val peerAdmittedAt = clock.instant()
        val peer =
            success(
                matrix.login(
                    CollaborationMatrixLogin(
                        n.fixture.company,
                        peerAccount,
                        UUID.randomUUID(),
                        peerAdmittedAt,
                        peerAdmittedAt.plusSeconds(60),
                    )
                )
            )
        val conversation = UUID.randomUUID()
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().use { client ->
            val source = HttpMatrixClientDataSource(client, configuration, Clock.systemUTC())
            val created =
                success(
                    InternalCollaborationMatrixRoomRepository(source, configuration.serverName)
                        .create(
                            CollaborationMatrixDirectRoom(
                                n.fixture.company,
                                conversation,
                                n.fixture.account,
                                peerAccount,
                            ),
                            owner.accessToken,
                            Instant.now().plusSeconds(20),
                        )
                )
            val roomId = (created as CollaborationMatrixRoomCreation.Created).roomId
            bindRoom(n, device, peerAccount, conversation, roomId)
            val room =
                CollaborationOwnedRoom(
                    n.fixture.company,
                    conversation,
                    n.fixture.account,
                    peerAccount,
                    n.fixture.account,
                    configuration.serverName,
                    roomId,
                )
            assertEquals(
                Result.Success(roomId),
                InternalCollaborationMatrixRoomAccessRepository(source)
                    .join(room, peer.accessToken, Instant.now().plusSeconds(20)),
            )
            val messages = InternalCollaborationMatrixRoomMessageRepository(source)
            val key = Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { 7 })
            fun send() =
                success(
                    messages.send(
                        roomId,
                        CollaborationRoomMessage(
                            UUID.randomUUID().toString(),
                            "Y2lwaGVydGV4dA",
                            key,
                            key,
                            peer.deviceId,
                        ),
                        peer.accessToken,
                        Instant.now().plusSeconds(20),
                    )
                )
            provider.stopProxy()
            val event = send()
            await("Provider must observe a failed callback before proxy recovery") {
                provider.providerHasFailedPush()
            }
            provider.startProxy(port)
            await("Provider retry must commit its exact event into the owned HRIS queue") {
                database()
                    .queryForList(
                        "select event_id from collaboration_push_hints where company_id=? and device_id=?",
                        n.fixture.company,
                        device,
                    )
                    .singleOrNull()
                    ?.get("event_id") == event
            }
            await("Successful callback must clear provider failure state") {
                !provider.providerHasFailedPush()
            }
            val head =
                database()
                    .queryForMap(
                        "select * from collaboration_push_hints where company_id=? and device_id=?",
                        n.fixture.company,
                        device,
                    )
            assertEquals(roomId, head["event_room_id"])
            assertEquals(0L, head["delivered_revision"])
            assertFalse(head.toString().contains("Y2lwaGVydGV4dA"))
            assertEquals(1, provider.pusherCount())
            database()
                .update(
                    "update native_sessions set revoked_at=now(),version=version+1 where id=?",
                    n.session,
                )
            send()
            await("A permanently retired HRIS parent must remove its provider pusher") {
                provider.pusherCount() == 0
            }
            assertEquals(
                event,
                database()
                    .queryForObject(
                        "select event_id from collaboration_push_hints where company_id=? and device_id=?",
                        String::class.java,
                        n.fixture.company,
                        device,
                    ),
            )
        }
    }

    private fun await(message: String, predicate: () -> Boolean) {
        val until = System.nanoTime() + Duration.ofSeconds(45).toNanos()
        while (System.nanoTime() < until) {
            if (predicate()) return
            Thread.sleep(200)
        }
        fail<Unit>(message)
    }

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
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

    private fun bindRoom(n: Native, device: UUID, peer: UUID, conversation: UUID, room: String) {
        // Owned SQL/protocol fixture; the actual publication worker has separate coverage.
        val db = database()
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
    }
}
