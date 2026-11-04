package dev.fajar.hris.worker

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.collaboration.data.matrix.MatrixHttpClientOwner
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.collaboration.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.worker.collaboration.CollaborationSpaceRoomWorker
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.json.JsonMapper

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties =
        [
            "hris.worker.enabled=true",
            "HRIS_COLLABORATION_ENABLED=true",
            "HRIS_MATRIX_ENABLED=true",
            "HRIS_COLLABORATION_SPACE_ENABLED=true",
            "HRIS_COLLABORATION_SPACE_PROVISIONING_ENABLED=true",
            "HRIS_COLLABORATION_SPACE_ROOM_PROVISIONING_ENABLED=true",
            "HRIS_COLLABORATION_SPACE_MIN_ANDROID_BUILD=28",
            "HRIS_IDENTITY_ACTIVE_KEY=fixture",
        ],
)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CollaborationSpaceRoomScheduledInteropTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18.6-alpine").withInitScript("worker-role.sql")
        @Container @JvmStatic val matrix = WorkerMatrixFixture()

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("HRIS_IDENTITY_KEYS") {
                "fixture:" + Base64.getEncoder().encodeToString(ByteArray(32) { 17 })
            }
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { "hris_worker_test" }
            registry.add("spring.datasource.password") { "worker-fixture-only" }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.url") { postgres.jdbcUrl }
            registry.add("spring.flyway.user") { postgres.username }
            registry.add("spring.flyway.password") { postgres.password }
            registry.add("HRIS_MATRIX_ORIGIN") { matrix.origin }
            registry.add("HRIS_MATRIX_SERVER_NAME") { "matrix.example.test" }
            registry.add("HRIS_MATRIX_JWT_ISSUER") { "urn:hris:matrix:fixture" }
            registry.add("HRIS_MATRIX_JWT_AUDIENCE") { "matrix:fixture" }
            registry.add("HRIS_MATRIX_JWT_SECRET_FILE") { matrix.signingFile.toString() }
        }
    }

    @Autowired private lateinit var select: SelectNativeCollaborationSpaceIdentity
    @Autowired private lateinit var register: RegisterNativeCollaborationSpaceDevice
    @Autowired private lateinit var queue: CollaborationSpaceProvisioningRepository
    @Autowired private lateinit var devices: CollaborationSpaceDeviceRepository
    @Autowired private lateinit var credentials: CollaborationSpaceProviderSessionRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var worker: CollaborationSpaceRoomWorker
    @Autowired private lateinit var http: MatrixHttpClientOwner

    @Autowired private lateinit var open: OpenNativeCollaborationSpaceDirect
    @Autowired private lateinit var prepare: RequestNativeCollaborationSpaceRoom
    @Autowired private lateinit var read: ReadNativeCollaborationSpaceRoom
    @Autowired private lateinit var rooms: CollaborationSpaceRoomRepository
    @Autowired private lateinit var protocol: CollaborationSpaceMatrixRoomRepository

    @Test
    fun actualScheduledRoomWorkerConnectsTwoCompaniesThroughOneEncryptedRoomAndPreservesReplay() {
        assertTrue(worker.isRunning)
        val first = registerEmployee()
        val second = registerEmployee()
        assertNotEquals(first.company, second.company)
        val space = first.state.spaceId
        val client = ClientVersion(ClientPlatform.ANDROID, 28)
        val conversation =
            success(
                    open.execute(
                        first.actor,
                        first.parent,
                        0,
                        space,
                        client,
                        UUID.randomUUID(),
                        second.actor.accountId,
                    )
                )
                .id
        val operation = UUID.randomUUID()
        fun request() =
            prepare.execute(
                first.actor,
                first.parent,
                0,
                space,
                operation,
                conversation,
                first.state.deviceId,
                1,
                null,
                client,
            )
        val receipt = success(request())
        val sql =
            JdbcTemplate(
                DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            )
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (
            sql.queryForObject(
                "select state from collaboration_space_room_provisioning where space_id=? and conversation_id=? and account_id=?",
                String::class.java,
                space,
                conversation,
                first.actor.accountId,
            ) != "SUCCEEDED"
        ) {
            check(System.nanoTime() < until) {
                "Owned scheduled room did not publish within its budget"
            }
            TimeUnit.MILLISECONDS.sleep(25)
        }
        val firstView =
            success(read.execute(first.actor, first.parent, 0, space, conversation, client))
        val secondView =
            success(read.execute(second.actor, second.parent, 0, space, conversation, client))
        assertEquals(firstView, secondView)
        val published = requireNotNull(firstView.room)
        assertEquals(CollaborationSpaceRoomOrigin.SPACE, published.origin)
        assertEquals(CollaborationSpaceProvisioningState.SUCCEEDED, firstView.provisioning?.state)
        val binding =
            requireNotNull(
                success(
                    transactions.run(first.actor) {
                        rooms.binding(space, first.actor.accountId, conversation)
                    }
                )
            )
        assertEquals(first.actor.accountId, binding.matrixCreatorAccountId)
        val observed =
            success(protocol.observe(published.roomId, first.token, Instant.now().plusSeconds(5)))
        assertEquals(first.state.matrixUserId, observed.creator)
        assertEquals("m.megolm.v1.aes-sha2", observed.encryptionAlgorithm)
        assertEquals("join", observed.members[first.state.matrixUserId])
        assertEquals("invite", observed.members[second.state.matrixUserId])
        assertEquals(receipt.copy(replayed = true), success(request()))
        success(
            prepare.execute(
                second.actor,
                second.parent,
                0,
                space,
                UUID.randomUUID(),
                conversation,
                second.state.deviceId,
                1,
                null,
                client,
            )
        )
        assertEquals(
            published,
            success(read.execute(second.actor, second.parent, 0, space, conversation, client)).room,
        )
        val releasedBy = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (
            sql.queryForObject(
                "select count(*) from collaboration_space_request_leases where space_id=? and account_id=?",
                Int::class.java,
                space,
                first.actor.accountId,
            ) != 0
        ) {
            check(System.nanoTime() < releasedBy) {
                "Owned completed room request slot was not released"
            }
            TimeUnit.MILLISECONDS.sleep(10)
        }
        for (person in listOf(first, second)) {
            assertEquals(
                person.state,
                success(
                    transactions.run(person.actor) {
                        credentials.metadata(space, person.actor.accountId, person.state.deviceId)
                    }
                ),
            )
            assertEquals(
                0,
                sql.queryForObject(
                    "select count(*) from collaboration_space_request_leases where space_id=? and account_id=?",
                    Int::class.java,
                    space,
                    person.actor.accountId,
                ),
            )
        }
        assertEquals(
            1,
            sql.queryForObject(
                "select count(*) from collaboration_space_room_provisioning where space_id=? and conversation_id=?",
                Int::class.java,
                space,
                conversation,
            ),
        )
        assertEquals(
            1,
            sql.queryForObject(
                "select count(*) from audit_entries where actor_id=? and action='collaboration.space_room_prepared'",
                Int::class.java,
                first.actor.accountId,
            ),
        )
    }

    private data class Person(
        val actor: Actor,
        val parent: UUID,
        val company: UUID,
        val state: CollaborationSpaceProviderState,
        val token: String,
    ) {
        override fun toString() = "Person(redacted)"
    }

    private fun registerEmployee(): Person {
        assertTrue(worker.isRunning)
        val sql =
            JdbcTemplate(
                DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            )
        val space =
            requireNotNull(
                sql.queryForObject("select id from collaboration_spaces", UUID::class.java)
            )
        val account = UUID.randomUUID()
        val company = UUID.randomUUID()
        val parent = UUID.randomUUID()
        val at = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS)
        sql.update(
            "insert into companies(id,code,name,timezone) values(?,?,'Fictional group worker','UTC')",
            company,
            company.toString().take(12),
        )
        sql.update(
            "insert into accounts(id,email,display_name) values(?,?,'Fictional group employee')",
            account,
            "$account@example.test",
        )
        sql.update(
            "insert into company_memberships(company_id,account_id) values(?,?)",
            company,
            account,
        )
        sql.update(
            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
            company,
            account,
        )
        sql.update(
            "insert into native_sessions(id,account_id,refresh_hash,authenticated_at,expires_at,access_expires_at,credential_version,created_at,rotated_at) values(?,?,?,?::timestamptz,?::timestamptz,?::timestamptz,0,?::timestamptz,?::timestamptz)",
            parent,
            account,
            parent.toString().replace("-", "").repeat(2),
            at.toString(),
            at.plusSeconds(86400).toString(),
            at.plusSeconds(900).toString(),
            at.toString(),
            at.toString(),
        )
        val actor = Actor(account, null, emptySet(), at, UUID.randomUUID(), credentialVersion = 0)
        val client = ClientVersion(ClientPlatform.ANDROID, 28)
        success(select.execute(actor, parent, 0, space, client, UUID.randomUUID()))
        val operation = UUID.randomUUID()
        fun request() =
            register.execute(
                actor,
                parent,
                0,
                space,
                client,
                operation,
                CollaborationPlatform.ANDROID,
                "Fictional device",
                CollaborationSpaceDeviceOrigin.SPACE,
            )
        val receipt = success(request())
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (
            sql.queryForObject(
                "select state from collaboration_space_provisioning where space_id=? and device_id=?",
                String::class.java,
                space,
                receipt.id,
            ) != "SUCCEEDED"
        ) {
            check(System.nanoTime() < until) {
                "Owned scheduled group request did not activate within its budget"
            }
            TimeUnit.MILLISECONDS.sleep(25)
        }
        val device =
            requireNotNull(
                success(transactions.run(actor) { devices.metadata(space, account, receipt.id) })
            )
        assertEquals(CollaborationDeviceState.ACTIVE, device.state)
        assertEquals(1, device.version)
        val head =
            requireNotNull(
                success(transactions.run(actor) { queue.metadata(space, account, receipt.id) })
            )
        assertEquals(CollaborationSpaceProvisioningState.SUCCEEDED, head.state)
        assertEquals(0, head.request.revision)
        val state =
            requireNotNull(
                success(
                    transactions.run(actor) { credentials.metadata(space, account, receipt.id) }
                )
            )
        val stored = success(transactions.run(actor) { credentials.credential(state) })
        assertEquals(0, state.generation)
        assertEquals(
            "@hrisg_${space.toString().replace("-", "")}_${account.toString().replace("-", "")}:matrix.example.test",
            state.matrixUserId,
        )
        assertEquals("HRISG_${receipt.id.toString().replace("-", "")}", state.matrixDeviceId)
        assertTrue(
            state.expiresAt > Instant.now() && state.expiresAt <= Instant.now().plusSeconds(600)
        )
        val who =
            http.client.send(
                HttpRequest.newBuilder(URI(matrix.origin + "/_matrix/client/v3/account/whoami"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer ${stored.accessToken}")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, who.statusCode())
        val identity = JsonMapper.builder().build().readTree(who.body())
        assertEquals(state.matrixUserId, identity.path("user_id").asString())
        assertEquals(state.matrixDeviceId, identity.path("device_id").asString())
        assertEquals(receipt.copy(replayed = true), success(request()))
        assertEquals(
            head,
            success(transactions.run(actor) { queue.metadata(space, account, receipt.id) }),
        )
        assertEquals(
            1,
            sql.queryForObject(
                "select count(*) from audit_entries where actor_id=? and action='collaboration.space_device_activated'",
                Int::class.java,
                account,
            ),
        )
        assertEquals(
            0,
            sql.queryForObject(
                "select count(*) from collaboration_provider_sessions where account_id=?",
                Int::class.java,
                account,
            ),
        )
        return Person(actor, parent, company, state, stored.accessToken)
    }

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result) {
            if (result is Result.Failed) result.failure.code else "Unexpected result"
        }
        return (result as Result.Success).value
    }
}
