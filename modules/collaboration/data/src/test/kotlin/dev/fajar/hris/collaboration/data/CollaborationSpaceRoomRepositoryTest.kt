package dev.fajar.hris.collaboration.data

import dev.fajar.hris.administration.domain.entities.ClientPlatform
import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.schema.tables.records.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CancellationException
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceRoomRepositoryTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun publicationUsesExactNativeAndProviderLeaseAndBothParticipantsRetainSafeMetadata() {
        val f = Fixture()
        assertNull(success(f.run { f.repo.intent(f.s.space, f.s.account, f.conversation) }))
        success(f.run { f.repo.createIntent(f.intent).flatMap { f.repo.enqueue(f.request) } })
        assertEquals(
            f.intent,
            success(f.run { f.repo.intent(f.s.space, f.s.account, f.conversation) }),
        )
        assertEquals(
            f.intent,
            success(
                f.run(f.s.actor.copy(accountId = f.peer)) {
                    f.repo.intent(f.s.space, f.peer, f.conversation)
                }
            ),
        )
        assertNull(
            success(
                f.run(f.s.actor.copy(accountId = f.peer)) {
                    f.repo.provisioning(f.s.space, f.s.account, f.conversation)
                }
            )
        )
        val status = success(f.run { f.repo.status(f.s.space, f.s.account, f.conversation) })!!
        assertEquals(
            status,
            success(
                f.run(f.s.actor.copy(accountId = f.peer)) {
                    f.repo.status(f.s.space, f.peer, f.conversation)
                }
            ),
        )
        assertNull(success(f.run { f.repo.status(f.s.space, UUID.randomUUID(), f.conversation) }))
        val lease =
            success(f.worker { f.workerRepo.claim(UUID.randomUUID(), 1, 60) }).single().lease!!
        assertEquals(f.request, lease.request)
        val q = lease.request
        val mismatches =
            listOf(
                q.copy(deviceVersion = q.deviceVersion + 1),
                q.copy(providerGeneration = q.providerGeneration + 1),
                q.copy(nativeSessionVersion = q.nativeSessionVersion + 1),
                q.copy(credentialVersion = q.credentialVersion + 1),
                q.copy(nativeSessionId = UUID.randomUUID()),
                q.copy(deviceId = UUID.randomUUID()),
                q.copy(conversationId = UUID.randomUUID()),
                q.copy(correlationId = UUID.randomUUID()),
                q.copy(authenticatedAt = q.authenticatedAt.plusNanos(1000)),
                q.copy(mfaVerifiedAt = q.authenticatedAt),
                q.copy(client = q.client.copy(build = 29)),
                q.copy(requestedAt = q.requestedAt.plusNanos(1000)),
                q.copy(expiresAt = q.expiresAt.minusNanos(1000)),
            )
        for (stale in mismatches) assertEquals(
            Result.Success(false),
            f.worker {
                f.workerRepo.finish(
                    lease.copy(request = stale),
                    CollaborationSpaceRoomFailure.PROVIDER_UNAVAILABLE,
                )
            },
        )
        for (stale in
            listOf(
                lease.copy(attempt = 2),
                lease.copy(token = UUID.randomUUID()),
                lease.copy(ownerId = UUID.randomUUID()),
                lease.copy(until = lease.until.minusNanos(1000)),
            )) {
            assertEquals(
                Result.Success(false),
                f.worker {
                    f.workerRepo.finish(stale, CollaborationSpaceRoomFailure.PROVIDER_UNAVAILABLE)
                },
            )
        }
        val binding = f.binding(lease)
        success(
            f.worker(f.s.actor) {
                f.workerRepo.publish(binding).flatMap {
                    f.workerRepo.finish(lease).map { assertTrue(it) }
                }
            }
        )
        assertEquals(
            binding,
            success(f.run { f.repo.binding(f.s.space, f.s.account, f.conversation) }),
        )
        assertEquals(
            binding,
            success(
                f.run(f.s.actor.copy(accountId = f.peer)) {
                    f.repo.binding(f.s.space, f.peer, f.conversation)
                }
            ),
        )
        assertEquals(
            CollaborationSpaceProvisioningState.SUCCEEDED,
            success(f.run { f.repo.status(f.s.space, f.s.account, f.conversation) })!!.state,
        )
        f.s.owner.update("delete from native_sessions where id=?", f.s.parent)
        assertEquals(
            binding,
            success(f.run { f.repo.binding(f.s.space, f.s.account, f.conversation) }),
        )
        assertEquals(Result.Success(false), f.worker { f.workerRepo.finish(lease) })
    }

    @Test
    fun failedIntentAndRequestReadbackRollBackTheirWritesAndPropagateCancellation() {
        val f = Fixture()
        for (stage in listOf("intent", "enqueue")) {
            if (stage == "enqueue") success(f.run { f.repo.createIntent(f.intent) })
            for (mode in listOf("missing", "scope", "time", "throw", "cancel", "interrupt")) {
                fun fail() {
                    when (mode) {
                        "throw" -> error("fixture readback")
                        "cancel" -> throw CancellationException("fixture readback")
                        "interrupt" -> throw InterruptedException("fixture readback")
                    }
                }
                val broken =
                    StoredCollaborationSpaceRoomRepository(
                        object : CollaborationSpaceRoomDataSource by f.raw {
                            override fun intent(
                                spaceId: UUID,
                                accountId: UUID,
                                conversationId: UUID,
                            ): CollaborationSpaceRoomIntentsRecord? {
                                val row = f.raw.intent(spaceId, accountId, conversationId)
                                if (stage == "intent") {
                                    assertNotNull(row)
                                    fail()
                                    when (mode) {
                                        "missing" -> return null
                                        "scope" -> row!!.spaceId = UUID.randomUUID()
                                        "time" -> row!!.createdAt = row.createdAt!!.plusNanos(1)
                                    }
                                }
                                return row
                            }

                            override fun find(
                                spaceId: UUID,
                                accountId: UUID,
                                conversationId: UUID,
                                lock: Boolean,
                            ): CollaborationSpaceRoomProvisioningRecord? {
                                val row = f.raw.find(spaceId, accountId, conversationId, lock)
                                if (stage == "enqueue") {
                                    assertNotNull(row)
                                    fail()
                                    when (mode) {
                                        "missing" -> return null
                                        "scope" -> row!!.spaceId = UUID.randomUUID()
                                        "time" -> row!!.requestedAt = row.requestedAt!!.plusNanos(1)
                                    }
                                }
                                return row
                            }
                        }
                    )
                val call = {
                    f.run {
                        if (stage == "intent") broken.createIntent(f.intent)
                        else broken.enqueue(f.request)
                    }
                }
                when (mode) {
                    "cancel" -> assertThrows(CancellationException::class.java) { call() }
                    "interrupt" -> assertThrows(InterruptedException::class.java) { call() }
                    else -> assertInstanceOf(Result.Failed::class.java, call(), "$stage/$mode")
                }
                if (stage == "intent")
                    assertNull(
                        success(f.run { f.repo.intent(f.s.space, f.s.account, f.conversation) })
                    )
                else
                    assertNull(
                        success(
                            f.run { f.repo.provisioning(f.s.space, f.s.account, f.conversation) }
                        )
                    )
            }
        }
    }

    @Test
    fun retryAndPublicationReadbackCannotCommitAnUncertainNewRevisionOrRoom() {
        val f = Fixture()
        success(f.run { f.repo.createIntent(f.intent).flatMap { f.repo.enqueue(f.request) } })
        assertTrue(success(f.run { f.repo.cancel(f.s.space, f.s.account, f.conversation, 0) }))
        val before =
            success(f.run { f.repo.provisioning(f.s.space, f.s.account, f.conversation) })!!
        val at = now()
        val retried =
            f.request.copy(revision = 1, requestedAt = at, expiresAt = at.plusSeconds(120))
        for (mode in listOf("missing", "scope", "throw", "cancel", "interrupt")) {
            var reads = 0
            val repo =
                StoredCollaborationSpaceRoomRepository(
                    object : CollaborationSpaceRoomDataSource by f.raw {
                        override fun find(
                            spaceId: UUID,
                            accountId: UUID,
                            conversationId: UUID,
                            lock: Boolean,
                        ): CollaborationSpaceRoomProvisioningRecord? {
                            val row = f.raw.find(spaceId, accountId, conversationId, lock)
                            reads++
                            if (reads == 2) {
                                assertEquals(1L, row!!.revision)
                                when (mode) {
                                    "missing" -> return null
                                    "scope" -> row.spaceId = UUID.randomUUID()
                                    "throw" -> error("fixture retry readback")
                                    "cancel" ->
                                        throw CancellationException("fixture retry readback")
                                    "interrupt" ->
                                        throw InterruptedException("fixture retry readback")
                                }
                            }
                            return row
                        }
                    }
                )
            val call = { f.run { repo.retry(retried, 0) } }
            when (mode) {
                "cancel" -> assertThrows(CancellationException::class.java) { call() }
                "interrupt" -> assertThrows(InterruptedException::class.java) { call() }
                else -> assertInstanceOf(Result.Failed::class.java, call())
            }
            assertEquals(2, reads)
            assertEquals(
                before,
                success(f.run { f.repo.provisioning(f.s.space, f.s.account, f.conversation) }),
            )
        }
        success(f.run { f.repo.retry(retried, 0) })
        assertEquals(
            "collaboration_room_provisioning_changed",
            (f.run { f.repo.retry(retried, 0) } as Result.Failed).failure.code,
        )
        val lease =
            success(f.worker { f.workerRepo.claim(UUID.randomUUID(), 1, 60) }).single().lease!!
        val binding = f.binding(lease)
        for (mode in listOf("missing", "owner", "scope", "throw", "cancel", "interrupt")) {
            val repo =
                StoredCollaborationSpaceRoomRepository(
                    object : CollaborationSpaceRoomDataSource by f.workerRaw {
                        override fun binding(
                            spaceId: UUID,
                            accountId: UUID,
                            conversationId: UUID,
                        ): CollaborationSpaceRoomBindingData? {
                            val row = f.workerRaw.binding(spaceId, accountId, conversationId)
                            assertNotNull(row)
                            when (mode) {
                                "missing" -> return null
                                "owner" -> row!!.binding.publishedBy = f.peer
                                "scope" -> row!!.intent.spaceId = UUID.randomUUID()
                                "throw" -> error("fixture room readback")
                                "cancel" -> throw CancellationException("fixture room readback")
                                "interrupt" -> throw InterruptedException("fixture room readback")
                            }
                            return row
                        }
                    }
                )
            val call = {
                f.worker(f.s.actor) {
                    repo.publish(binding).flatMap {
                        f.workerRepo.finish(lease).map { assertTrue(it) }
                    }
                }
            }
            when (mode) {
                "cancel" -> assertThrows(CancellationException::class.java) { call() }
                "interrupt" -> assertThrows(InterruptedException::class.java) { call() }
                else -> assertInstanceOf(Result.Failed::class.java, call())
            }
            assertNull(success(f.run { f.repo.binding(f.s.space, f.s.account, f.conversation) }))
            assertEquals(
                lease,
                success(f.run { f.repo.provisioning(f.s.space, f.s.account, f.conversation) })!!
                    .lease,
            )
        }
        success(
            f.worker(f.s.actor) {
                f.workerRepo.publish(binding).flatMap {
                    f.workerRepo.finish(lease).map { assertTrue(it) }
                }
            }
        )
        assertEquals(
            binding,
            success(f.run { f.repo.binding(f.s.space, f.s.account, f.conversation) }),
        )
    }

    @Test
    fun malformedIntentAndPeerStatusNeverProducePartialOrForeignMetadata() {
        val f = Fixture()
        success(f.run { f.repo.createIntent(f.intent).flatMap { f.repo.enqueue(f.request) } })
        for (mode in listOf("creator", "scope", "user", "partialOriginal", "time")) {
            val repo =
                StoredCollaborationSpaceRoomRepository(
                    object : CollaborationSpaceRoomDataSource by f.raw {
                        override fun intent(
                            spaceId: UUID,
                            accountId: UUID,
                            conversationId: UUID,
                        ): CollaborationSpaceRoomIntentsRecord? =
                            f.raw.intent(spaceId, accountId, conversationId)?.also {
                                when (mode) {
                                    "creator" -> it.matrixCreatorAccountId = f.peer
                                    "scope" -> it.spaceId = UUID.randomUUID()
                                    "user" -> it.firstMatrixUserId = "@foreign:matrix.example.test"
                                    "partialOriginal" -> it.legacyCompanyId = f.s.company
                                    "time" -> it.createdAt = it.createdAt!!.plusNanos(1)
                                }
                            }
                    }
                )
            assertInstanceOf(
                Result.Failed::class.java,
                f.run { repo.intent(f.s.space, f.s.account, f.conversation) },
                mode,
            )
        }
        for (mode in
            listOf(
                "duplicate",
                "scope",
                "conversation",
                "state",
                "time",
                "failureOnly",
                "outcomeOnly",
                "revision",
                "throw",
                "cancel",
                "interrupt",
            )) {
            val repo =
                StoredCollaborationSpaceRoomRepository(
                    object : CollaborationSpaceRoomDataSource by f.raw {
                        override fun status(
                            spaceId: UUID,
                            accountId: UUID,
                            conversationId: UUID,
                        ): List<CollaborationSpaceRoomStatusRecord> {
                            val rows = f.raw.status(spaceId, accountId, conversationId)
                            when (mode) {
                                "throw" -> error("fixture status acquisition")
                                "cancel" ->
                                    throw CancellationException("fixture status acquisition")
                                "interrupt" ->
                                    throw InterruptedException("fixture status acquisition")
                                "duplicate" -> return rows + rows
                            }
                            return rows.also {
                                val row = it.single()
                                when (mode) {
                                    "scope" -> row.spaceId = UUID.randomUUID()
                                    "conversation" -> row.conversationId = UUID.randomUUID()
                                    "state" -> row.state = "RUNNING"
                                    "time" -> row.requestedAt = row.requestedAt!!.plusNanos(1)
                                    "failureOnly" -> row.failureCode = "ROOM_MISMATCH"
                                    "outcomeOnly" -> row.lastOutcomeAt = row.requestedAt
                                    "revision" -> row.revision = 1
                                }
                            }
                        }
                    }
                )
            val call = {
                f.run(f.s.actor.copy(accountId = f.peer)) {
                    repo.status(f.s.space, f.peer, f.conversation)
                }
            }
            when (mode) {
                "cancel" -> assertThrows(CancellationException::class.java) { call() }
                "interrupt" -> assertThrows(InterruptedException::class.java) { call() }
                else -> assertInstanceOf(Result.Failed::class.java, call(), mode)
            }
        }
        assertEquals(
            CollaborationSpaceProvisioningState.QUEUED,
            success(f.run { f.repo.status(f.s.space, f.s.account, f.conversation) })!!.state,
        )
    }

    @Test
    fun claimBoundsAndMalformedClaimReadbackCannotRetainAFalseWorkerLease() {
        val f = Fixture()
        success(f.run { f.repo.createIntent(f.intent).flatMap { f.repo.enqueue(f.request) } })
        var calls = 0
        var mode = "throw"
        val repo =
            StoredCollaborationSpaceRoomRepository(
                object : CollaborationSpaceRoomDataSource by f.workerRaw {
                    override fun claim(
                        ownerId: UUID,
                        limit: Int,
                        leaseSeconds: Int,
                    ): List<CollaborationSpaceRoomProvisioningRecord> {
                        calls++
                        val rows = f.workerRaw.claim(ownerId, limit, leaseSeconds)
                        assertEquals(1, rows.size)
                        when (mode) {
                            "throw" -> error("fixture claim")
                            "cancel" -> throw CancellationException("fixture claim")
                            "interrupt" -> throw InterruptedException("fixture claim")
                            "duplicate" -> return rows + rows
                            "owner" -> rows.single().leaseOwner = UUID.randomUUID()
                            "partial" -> rows.single().leaseToken = null
                            "state" -> rows.single().state = "QUEUED"
                        }
                        return rows
                    }
                }
            )
        val owner = UUID.randomUUID()
        for ((limit, seconds) in listOf(0 to 1, 9 to 1, 1 to 0, 1 to 61)) assertInstanceOf(
            Result.Failed::class.java,
            f.worker { repo.claim(owner, limit, seconds) },
        )
        assertInstanceOf(Result.Failed::class.java, f.worker { repo.claim(UUID(0, 0), 1, 30) })
        assertEquals(0, calls)
        for (outcome in
            listOf("throw", "cancel", "interrupt", "duplicate", "owner", "partial", "state")) {
            mode = outcome
            val call = { f.worker { repo.claim(owner, 1, 30) } }
            when (outcome) {
                "cancel" -> assertThrows(CancellationException::class.java) { call() }
                "interrupt" -> assertThrows(InterruptedException::class.java) { call() }
                else -> assertInstanceOf(Result.Failed::class.java, call(), outcome)
            }
            val stored =
                success(f.run { f.repo.provisioning(f.s.space, f.s.account, f.conversation) })!!
            assertEquals(CollaborationSpaceProvisioningState.QUEUED, stored.state)
            assertEquals(0, stored.attempts)
            assertNull(stored.lease)
        }
        assertEquals(7, calls)
    }

    private inner class Fixture {
        val s = SpaceGatewayPostgresFixture(postgres).also { it.migrate() }
        val peer = UUID.randomUUID()
        val otherCompany = UUID.randomUUID()
        val conversation = UUID.randomUUID()
        val pair = listOf(s.account, peer).sortedBy(UUID::toString)

        init {
            Flyway.configure().dataSource(s.source).target("133").load().migrate()
            s.owner.execute(
                "grant select,insert,update,delete on all tables in schema public to space_gateway_fixture,space_gateway_worker"
            )
            s.owner.update(
                "insert into companies(id,code,name,timezone) values(?,?,'Fictional other company','UTC')",
                otherCompany,
                otherCompany.toString().replace("-", ""),
            )
            s.owner.update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional room peer')",
                peer,
                "$peer@example.test",
            )
            s.owner.update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                otherCompany,
                peer,
            )
            s.owner.update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'collaboration.chat')",
                otherCompany,
                peer,
            )
            s.owner.update(
                "insert into collaboration_space_identities(space_id,account_id,matrix_server_name,matrix_user_id,origin,created_at) values(?,?,'matrix.example.test',?,'SPACE',?::timestamptz)",
                s.space,
                peer,
                user(peer),
                s.at.toString(),
            )
            s.owner.update(
                "insert into collaboration_space_direct_conversations(space_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,?::timestamptz)",
                s.space,
                conversation,
                pair[0],
                pair[1],
                peer,
                s.at.toString(),
            )
        }

        val raw =
            PostgresCollaborationSpaceRoomDataSource(
                DSL.using(TransactionAwareDataSourceProxy(s.runtimeSource), SQLDialect.POSTGRES)
            )
        val repo = StoredCollaborationSpaceRoomRepository(raw)
        val workerRaw =
            PostgresCollaborationSpaceRoomDataSource(
                DSL.using(TransactionAwareDataSourceProxy(s.workerSource), SQLDialect.POSTGRES)
            )
        val workerRepo = StoredCollaborationSpaceRoomRepository(workerRaw)
        val workerTransactions =
            PostgresTransactionRunner(
                JdbcTransactionManager(s.workerSource),
                JdbcTemplate(s.workerSource),
            )
        val intent =
            CollaborationSpaceRoomIntent(
                s.space,
                conversation,
                pair[0],
                pair[1],
                s.account,
                s.account,
                "matrix.example.test",
                user(pair[0]),
                user(pair[1]),
                CollaborationSpaceRoomOrigin.SPACE,
                null,
                s.at,
            )
        val requestedAt = now()
        val request =
            CollaborationSpaceRoomRequest(
                s.space,
                conversation,
                s.account,
                s.device,
                1,
                0,
                s.parent,
                0,
                0,
                0,
                s.actor.authenticatedAt,
                null,
                UUID.randomUUID(),
                ClientVersion(ClientPlatform.ANDROID, 28),
                requestedAt,
                requestedAt.plusSeconds(120),
            )

        fun user(account: UUID) =
            "@hrisg_${s.space.toString().replace("-", "")}_${account.toString().replace("-", "")}:matrix.example.test"

        fun binding(lease: CollaborationSpaceRoomLease) =
            CollaborationSpaceRoomBinding(
                s.space,
                conversation,
                s.account,
                lease.request.revision,
                lease.token,
                "!original",
                now(),
                s.account,
            )

        fun <T> run(actor: Actor = s.actor, body: () -> Result<T>) = s.tx(actor, body)

        fun <T> worker(actor: Actor = s.actor.copy(accountId = UUID(0, 0)), body: () -> Result<T>) =
            workerTransactions.run(actor, body)
    }

    private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)

    private fun <T> success(result: Result<T>): T = (result as Result.Success).value
}
