package dev.fajar.hris

import java.net.URI
import java.net.http.*
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

/** Owned HTTP/SQL setup. Provider room creation is real; binding publication is a fixture. */
abstract class NativeGatewaySdkFixture : NativeCollaborationApiFixture() {
    private val parents = mutableSetOf<UUID>()

    @BeforeEach
    fun alignSdkClock() {
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS))
    }

    @AfterEach
    fun revokeSdkParents() {
        for (parent in parents) database()
            .update(
                "update native_sessions set revoked_at=coalesce(revoked_at,now()),version=version+1 where id=?",
                parent,
            )
    }

    protected class SdkOwner(
        val native: Native,
        val device: UUID,
        val session: Map<String, String>,
    ) {
        override fun toString() = "SdkOwner(redacted)"
    }

    protected fun sdkPeer(owner: Native): Native {
        val id = UUID.randomUUID()
        val email = "$id@example.test"
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Fictional native SDK peer',password_hash from accounts where id=?",
                id,
                email,
                owner.fixture.account,
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                owner.fixture.company,
                id,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                owner.fixture.company,
                id,
            )
        val browser = client()
        val csrf = login(browser, email)
        val response =
            command(
                browser,
                "/api/v1/auth/native/exchange",
                "{\"deviceName\":\"Fictional SDK peer\"}",
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, response.statusCode(), response.body())
        val value = json.readTree(response.body())
        return Native(
            owner.fixture.copy(
                browser = browser,
                csrf = csrf,
                account = id,
                actor =
                    owner.fixture.actor.copy(
                        accountId = id,
                        permissions = setOf("collaboration.chat"),
                    ),
            ),
            UUID.fromString(value.path("sessionId").asString()),
            value.path("accessToken").asString(),
        )
    }

    protected fun sdkOwner(native: Native): SdkOwner {
        parents.add(native.session)
        val registered = register(native)
        assertEquals(200, registered.statusCode(), registered.body())
        val device = UUID.fromString(json.readTree(registered.body()).path("id").asString())
        database()
            .update(
                "update collaboration_devices set state='ACTIVE',version=version+1,updated_at=now() where id=?",
                device,
            )
        val operation = UUID.randomUUID()
        val response =
            native.fixture.browser.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port${native.path}/bootstrap"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer ${native.access}")
                    .header("Content-Type", "application/json")
                    .header("X-HRIS-Client-Platform", "ANDROID")
                    .header("X-HRIS-Client-Build", "1")
                    .header("Idempotency-Key", operation.toString())
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
        val value = json.readTree(response.body())
        return SdkOwner(
            native,
            device,
            mapOf(
                "userId" to value.path("matrixUserId").asString(),
                "deviceId" to value.path("matrixDeviceId").asString(),
                "accessToken" to value.path("accessToken").asString(),
                "refreshToken" to value.path("refreshToken").asString(),
                "accountId" to native.fixture.account.toString(),
                "companyId" to native.fixture.company.toString(),
                "nativeSessionId" to native.session.toString(),
                "hrisDeviceId" to device.toString(),
                "bootstrapOperationId" to operation.toString(),
                "gatewaySessionId" to value.path("sessionId").asString(),
                "gatewayVersion" to value.path("version").asLong().toString(),
                "accessExpiresAt" to value.path("accessExpiresAt").asString(),
                "expiresAt" to value.path("expiresAt").asString(),
                "clientBuild" to "1",
            ),
        )
    }

    protected fun publishSdkRoom(
        n: Native,
        device: UUID,
        peer: UUID,
        room: String,
        conversation: UUID,
    ): String {
        // Owned SQL/protocol fixture; the actual publication worker has separate coverage.
        val db = database()
        val job = UUID.randomUUID()
        val at = java.sql.Timestamp.from(clock.instant())
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
        return room
    }
}
