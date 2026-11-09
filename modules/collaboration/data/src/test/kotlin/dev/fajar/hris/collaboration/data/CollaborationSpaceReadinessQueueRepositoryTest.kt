package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationSpaceReadinessQueueRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.schema.tables.records.CollaborationSpaceReadinessDeliveriesRecord
import dev.fajar.hris.schema.tables.records.CollaborationSpaceReadinessTargetsRecord
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CancellationException
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationSpaceReadinessQueueRepositoryTest {
    @Container
    val postgres = PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))

    @Test
    fun typedChannelsReadExactTargetsAndCompareTheWholeCheckpointBeforeProgress() {
        val f = Fixture()
        val matrix =
            success(f.tx { f.repo.claim(CollaborationSpaceReadinessChannel.MATRIX, f.owner, 1) })
                .single()
        val push =
            success(f.tx { f.repo.claim(CollaborationSpaceReadinessChannel.PUSH, f.owner, 1) })
                .single()
        assertEquals(matrix.eventId, push.eventId)
        assertNotEquals(matrix.token, push.token)
        val checkpoint = requireNotNull(success(f.tx { f.repo.find(matrix, lock = true) }))
        val target = success(f.tx { f.repo.targets(checkpoint, 8) }).single()
        assertEquals(
            CollaborationReadinessTarget(
                CollaborationReadinessPosition(f.e.peer, f.e.peerDevice),
                f.e.peerParent,
            ),
            target,
        )
        for (changed in
            listOf(
                checkpoint.copy(publicationRevision = checkpoint.publicationRevision + 1),
                checkpoint.copy(capturedAt = checkpoint.capturedAt.minusNanos(1000)),
                checkpoint.copy(attempts = checkpoint.attempts + 1),
            )) {
            assertTrue(success(f.tx { f.repo.targets(changed, 8) }).isEmpty())
            assertFalse(success(f.tx { f.repo.advance(changed, target.position) }))
            assertFalse(success(f.tx { f.repo.release(changed, false, now()) }))
        }
        assertNull(success(f.tx { f.repo.find(matrix.copy(token = UUID.randomUUID()), false) }))
        assertTrue(success(f.tx { f.repo.advance(checkpoint, target.position) }))
        assertFalse(success(f.tx { f.repo.advance(checkpoint, target.position) }))
        val progressed = requireNotNull(success(f.tx { f.repo.find(matrix, false) }))
        assertEquals(checkpoint.copy(after = target.position, attempts = 0), progressed)
        assertTrue(success(f.tx { f.repo.targets(progressed, 8) }).isEmpty())
        assertTrue(success(f.tx { f.repo.release(progressed, true, now()) }))
        assertFalse(success(f.tx { f.repo.release(progressed, true, now()) }))
        assertNull(success(f.tx { f.repo.find(matrix, false) }))
        assertNotNull(success(f.tx { f.repo.find(push, false) }))
        assertTrue(
            success(f.tx { f.repo.claim(CollaborationSpaceReadinessChannel.MATRIX, f.owner, 1) })
                .isEmpty()
        )
    }

    @Test
    fun falseOrInterruptedPostWriteReadbackRollsBackActualCursorAndRelease() {
        val f = Fixture()
        val lease =
            success(f.tx { f.repo.claim(CollaborationSpaceReadinessChannel.MATRIX, f.owner, 1) })
                .single()
        val original = requireNotNull(success(f.tx { f.repo.find(lease, false) }))
        val next = CollaborationReadinessPosition(f.e.peer, f.e.peerDevice)
        for (release in listOf(false, true)) for (mode in
            listOf("missing", "scope", "revision", "precision", "cancel", "interrupt", "throw")) {
            var wrote = false
            fun corrupt(
                row: CollaborationSpaceReadinessDeliveriesRecord?
            ): CollaborationSpaceReadinessDeliveriesRecord? {
                if (!wrote) return row
                when (mode) {
                    "missing" -> return null
                    "cancel" -> throw CancellationException("fixture readback")
                    "interrupt" -> throw InterruptedException("fixture readback")
                    "throw" -> error("fixture readback")
                }
                return requireNotNull(row).also {
                    when (mode) {
                        "scope" -> it.spaceId = UUID.randomUUID()
                        "revision" ->
                            it.publicationRevision = requireNotNull(it.publicationRevision) + 1
                        "precision" -> it.capturedAt = requireNotNull(it.capturedAt).plusNanos(1)
                    }
                }
            }
            val faulty =
                object : CollaborationSpaceReadinessQueueDataSource by f.source {
                    override fun advance(
                        checkpoint: CollaborationSpaceReadinessCheckpointData,
                        accountId: UUID,
                        deviceId: UUID,
                    ): Boolean =
                        f.source.advance(checkpoint, accountId, deviceId).also { wrote = it }

                    override fun release(
                        checkpoint: CollaborationSpaceReadinessCheckpointData,
                        complete: Boolean,
                        availableAt: OffsetDateTime,
                    ): Boolean =
                        f.source.release(checkpoint, complete, availableAt).also { wrote = it }

                    override fun find(lease: CollaborationSpaceReadinessLeaseData, lock: Boolean) =
                        corrupt(f.source.find(lease, lock))

                    override fun read(eventId: UUID, channel: String) =
                        corrupt(f.source.read(eventId, channel))
                }
            val repo = StoredCollaborationSpaceReadinessQueueRepository(faulty)
            val action = {
                f.tx {
                    if (release) repo.release(original, true, now())
                    else repo.advance(original, next)
                }
            }
            when (mode) {
                "cancel" -> assertThrows(CancellationException::class.java) { action() }
                "interrupt" -> assertThrows(InterruptedException::class.java) { action() }
                else -> assertInstanceOf(Result.Failed::class.java, action(), mode)
            }
            assertTrue(wrote, mode)
            assertEquals(original, success(f.tx { f.repo.find(lease, false) }), mode)
        }
    }

    @Test
    fun malformedClaimAndTargetRowsFailInsideTheAcquisitionBoundaryWithoutLeakingAClaim() {
        val f = Fixture()
        for (mode in
            listOf(
                "scope",
                "channel",
                "zero",
                "attempts",
                "precision",
                "duplicates",
                "cancel",
                "interrupt",
            )) {
            val source =
                object : CollaborationSpaceReadinessQueueDataSource by f.source {
                    override fun claim(
                        channel: String,
                        owner: UUID,
                        limit: Int,
                    ): List<CollaborationSpaceReadinessDeliveriesRecord> {
                        val rows = f.source.claim(channel, owner, limit)
                        assertEquals(1, rows.size)
                        when (mode) {
                            "cancel" -> throw CancellationException("fixture claim")
                            "interrupt" -> throw InterruptedException("fixture claim")
                            "duplicates" -> return rows + rows
                        }
                        rows.single().also {
                            when (mode) {
                                "scope" -> it.leaseOwner = UUID.randomUUID()
                                "channel" -> it.channel = "PUSH"
                                "zero" -> it.leaseToken = UUID(0, 0)
                                "attempts" -> it.attempts = 0
                                "precision" ->
                                    it.capturedAt = requireNotNull(it.capturedAt).plusNanos(1)
                            }
                        }
                        return rows
                    }
                }
            val repo = StoredCollaborationSpaceReadinessQueueRepository(source)
            val action = {
                f.tx { repo.claim(CollaborationSpaceReadinessChannel.MATRIX, f.owner, 4) }
            }
            when (mode) {
                "cancel" -> assertThrows(CancellationException::class.java) { action() }
                "interrupt" -> assertThrows(InterruptedException::class.java) { action() }
                else -> assertInstanceOf(Result.Failed::class.java, action(), mode)
            }
        }
        val lease =
            success(f.tx { f.repo.claim(CollaborationSpaceReadinessChannel.MATRIX, f.owner, 1) })
                .single()
        val checkpoint = requireNotNull(success(f.tx { f.repo.find(lease, false) }))
        assertEquals(1, checkpoint.attempts, "Every rejected claim rolled back")
        for (mode in
            listOf(
                "scope",
                "channel",
                "lease",
                "revision",
                "cutoff",
                "parent",
                "own",
                "duplicate",
            )) {
            val source =
                object : CollaborationSpaceReadinessQueueDataSource by f.source {
                    override fun targets(
                        checkpoint: CollaborationSpaceReadinessCheckpointData,
                        limit: Int,
                    ): List<CollaborationSpaceReadinessTargetsRecord> {
                        val rows = f.source.targets(checkpoint, limit)
                        assertEquals(1, rows.size)
                        if (mode == "duplicate") return rows + rows
                        rows.single().also {
                            when (mode) {
                                "scope" -> it.spaceId = UUID.randomUUID()
                                "channel" -> it.channel = "PUSH"
                                "lease" -> it.leaseToken = UUID.randomUUID()
                                "revision" ->
                                    it.publicationRevision =
                                        requireNotNull(it.publicationRevision) + 1
                                "cutoff" ->
                                    it.publicationCutoff =
                                        requireNotNull(it.publicationCutoff).plusNanos(1000)
                                "parent" -> it.nativeSessionId = UUID(0, 0)
                                "own" -> it.targetAccountId = f.e.s.account
                            }
                        }
                        return rows
                    }
                }
            val repo = StoredCollaborationSpaceReadinessQueueRepository(source)
            assertInstanceOf(Result.Failed::class.java, f.tx { repo.targets(checkpoint, 8) }, mode)
            assertEquals(1, success(f.tx { f.repo.targets(checkpoint, 8) }).size)
        }
    }

    @Test
    fun invalidCallerBoundsAndCoordinatesNeverAcquireTheDatasource() {
        var calls = 0
        fun <T> forbidden(): T {
            calls++
            error("Unexpected fixture I/O")
        }
        val source =
            object : CollaborationSpaceReadinessQueueDataSource {
                override fun claim(
                    channel: String,
                    owner: UUID,
                    limit: Int,
                ): List<CollaborationSpaceReadinessDeliveriesRecord> = forbidden()

                override fun find(
                    lease: CollaborationSpaceReadinessLeaseData,
                    lock: Boolean,
                ): CollaborationSpaceReadinessDeliveriesRecord? = forbidden()

                override fun read(
                    eventId: UUID,
                    channel: String,
                ): CollaborationSpaceReadinessDeliveriesRecord? = forbidden()

                override fun targets(
                    checkpoint: CollaborationSpaceReadinessCheckpointData,
                    limit: Int,
                ): List<CollaborationSpaceReadinessTargetsRecord> = forbidden()

                override fun advance(
                    checkpoint: CollaborationSpaceReadinessCheckpointData,
                    accountId: UUID,
                    deviceId: UUID,
                ): Boolean = forbidden()

                override fun release(
                    checkpoint: CollaborationSpaceReadinessCheckpointData,
                    complete: Boolean,
                    availableAt: OffsetDateTime,
                ): Boolean = forbidden()
            }
        val repo = StoredCollaborationSpaceReadinessQueueRepository(source)
        val at = now()
        val lease =
            CollaborationSpaceReadinessLease(
                UUID.randomUUID(),
                CollaborationSpaceReadinessChannel.MATRIX,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                at.plusSeconds(60),
            )
        val checkpoint =
            CollaborationSpaceReadinessCheckpoint(
                lease,
                1,
                at.minusSeconds(60),
                at,
                at.minusSeconds(60).plusSeconds(86400),
                at,
                1,
                null,
            )
        val next = CollaborationReadinessPosition(UUID.randomUUID(), UUID.randomUUID())
        for (limit in listOf(0, 5, Int.MAX_VALUE)) assertInstanceOf(
            Result.Failed::class.java,
            repo.claim(lease.channel, lease.ownerId, limit),
        )
        assertInstanceOf(Result.Failed::class.java, repo.claim(lease.channel, UUID(0, 0), 1))
        assertInstanceOf(
            Result.Failed::class.java,
            repo.find(lease.copy(token = UUID(0, 0)), false),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            repo.find(lease.copy(until = at.plusNanos(1)), false),
        )
        for (limit in listOf(0, 9)) assertInstanceOf(
            Result.Failed::class.java,
            repo.targets(checkpoint, limit),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            repo.targets(
                checkpoint.copy(after = next.copy(accountId = lease.publisherAccountId)),
                8,
            ),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            repo.advance(checkpoint, next.copy(accountId = lease.publisherAccountId)),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            repo.advance(checkpoint.copy(after = next), next),
        )
        assertInstanceOf(
            Result.Failed::class.java,
            repo.release(checkpoint, true, at.minusSeconds(1)),
        )
        assertInstanceOf(Result.Failed::class.java, repo.release(checkpoint, true, at.plusNanos(1)))
        assertEquals(0, calls)
    }

    @Test
    fun guardedRecipientAcknowledgesOnlyItsOwnCursorAndRollsBackLateFailureOrCancellation() {
        val f = Fixture()
        val lease =
            success(f.tx { f.repo.claim(CollaborationSpaceReadinessChannel.MATRIX, f.owner, 1) })
                .single()
        val original = requireNotNull(success(f.tx { f.repo.find(lease, false) }))
        val next = CollaborationReadinessPosition(f.e.peer, f.e.peerDevice)
        val jdbc = f.e.worker
        for (cancel in listOf(false, true)) {
            var wrote = false
            val action = {
                f.e.workerTransactions.run(f.e.peerActor) {
                    jdbc.queryForObject(
                        "select id from accounts where id=? for share",
                        UUID::class.java,
                        f.e.peer,
                    )
                    jdbc.queryForObject(
                        "select id from native_sessions where id=? and account_id=? for update",
                        UUID::class.java,
                        f.e.peerParent,
                        f.e.peer,
                    )
                    f.repo.advance(original, next).flatMap { changed ->
                        assertTrue(
                            changed,
                            "Current guarded recipient must acknowledge its own target",
                        )
                        wrote = true
                        if (cancel) throw CancellationException("fixture late target check")
                        Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_late_target_check"))
                    }
                }
            }
            if (cancel) assertThrows(CancellationException::class.java) { action() }
            else assertInstanceOf(Result.Failed::class.java, action())
            assertTrue(wrote, "Recipient progression must run before the injected late failure")
            assertEquals(original, success(f.tx { f.repo.find(lease, false) }))
        }
        for (actor in
            listOf(
                f.e.peerActor.copy(accountId = UUID.randomUUID()),
                f.e.peerActor.copy(companyId = f.e.peerCompany),
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                f.e.workerTransactions.run(actor) { f.repo.advance(original, next) },
            )
        }
        assertInstanceOf(
            Result.Failed::class.java,
            f.e.workerTransactions.run(f.e.peerActor) {
                f.repo.advance(original, next.copy(accountId = UUID.randomUUID()))
            },
        )
        f.e.s.owner.execute(
            "grant select,update on collaboration_space_readiness_deliveries to space_gateway_fixture"
        )
        val api =
            StoredCollaborationSpaceReadinessQueueRepository(
                PostgresCollaborationSpaceReadinessQueueDataSource(f.e.base.sql)
            )
        assertFalse(success(f.e.s.tx(f.e.peerActor) { api.advance(original, next) }))
        assertTrue(
            success(f.e.workerTransactions.run(f.e.peerActor) { f.repo.advance(original, next) })
        )
        val progressed = requireNotNull(success(f.tx { f.repo.find(lease, false) }))
        assertEquals(original.copy(after = next, attempts = 0), progressed)
        assertInstanceOf(
            Result.Failed::class.java,
            f.e.workerTransactions.run(f.e.peerActor) { f.repo.release(progressed, true, now()) },
        )
        assertEquals(progressed, success(f.tx { f.repo.find(lease, false) }))
        assertTrue(success(f.tx { f.repo.release(progressed, true, now()) }))
    }

    @Test
    fun targetAcknowledgementMigrationPreservesTheOriginalPublicationAndActiveLease() {
        val f = Fixture(schema = "139")
        val lease =
            success(f.tx { f.repo.claim(CollaborationSpaceReadinessChannel.MATRIX, f.owner, 1) })
                .single()
        val checkpoint = requireNotNull(success(f.tx { f.repo.find(lease, false) }))
        val before =
            f.e.s.owner.queryForList(
                "select * from collaboration_space_readiness_deliveries order by event_id,channel"
            )
        val revisions =
            f.e.s.owner.queryForList("select * from collaboration_space_readiness_revisions")
        val keys =
            f.e.s.owner.queryForList(
                "select * from collaboration_space_key_publications order by space_id,device_id"
            )
        Flyway.configure().dataSource(f.e.s.source).target("140").load().migrate()
        assertEquals(
            before,
            f.e.s.owner.queryForList(
                "select * from collaboration_space_readiness_deliveries order by event_id,channel"
            ),
        )
        assertEquals(
            revisions,
            f.e.s.owner.queryForList("select * from collaboration_space_readiness_revisions"),
        )
        assertEquals(
            keys,
            f.e.s.owner.queryForList(
                "select * from collaboration_space_key_publications order by space_id,device_id"
            ),
        )
        assertEquals(checkpoint, success(f.tx { f.repo.find(lease, false) }))
        val target = success(f.tx { f.repo.targets(checkpoint, 8) }).single()
        assertTrue(
            success(
                f.e.workerTransactions.run(f.e.peerActor) {
                    f.repo.advance(checkpoint, target.position)
                }
            )
        )
    }

    private inner class Fixture(schema: String = "140") {
        val e = SpaceKeyExchangeFixture(postgres)
        val owner = UUID.randomUUID()
        val source =
            PostgresCollaborationSpaceReadinessQueueDataSource(
                DSL.using(TransactionAwareDataSourceProxy(e.s.workerSource), SQLDialect.POSTGRES)
            )
        val repo = StoredCollaborationSpaceReadinessQueueRepository(source)

        init {
            // Preserve confirmed pre-V139 peer keys, then capture one new journal normally.
            e.reserve()
            e.reserve(account = e.peer)
            e.bind()
            Flyway.configure().dataSource(e.s.source).target(schema).load().migrate()
            success(
                e.s.tx {
                    e.base.journal.record(
                        e.s.actor,
                        ChangeRecord(
                            "collaboration_space_device",
                            e.s.device,
                            "collaboration.space_device_keys_published",
                        ),
                    )
                }
            )
        }

        fun <T> tx(body: () -> Result<T>) = e.workerTransactions.run(e.s.actor, body)
    }

    private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }
}
