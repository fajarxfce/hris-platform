package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.StoredCollaborationSpaceRequestRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.schema.tables.records.CollaborationSpaceRequestLeasesRecord
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.*
import javax.sql.DataSource
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
class CollaborationSpaceRequestRepositoryTest {
    @Container val postgres = PostgreSQLContainer("postgres:18.6-alpine")

    @Test
    fun typedClaimAndReleaseKeepOwnedProvenanceAcrossOffboardingAndNativePurge() {
        val f = Fixture()
        val lease = requireNotNull(success(f.claim()))
        assertEquals(f.position, lease.position)
        assertEquals(1, lease.slot)
        assertEquals("CollaborationSpaceRequestLease(<redacted>)", lease.toString())
        assertEquals(lease, success(f.storage.tx { f.repo.find(f.owner, lease.slot, lock = true) }))
        val before = f.provenance()
        f.storage.owner.update(
            "update company_memberships set active=false,version=version+1 where account_id=?",
            f.owner.accountId,
        )
        f.storage.owner.update("delete from native_sessions where id=?", f.owner.nativeSessionId)
        assertEquals(lease, success(f.storage.tx { f.repo.find(f.owner, lease.slot) }))
        assertInstanceOf(Result.Failed::class.java, f.claim())
        assertTrue(success(f.storage.tx { f.repo.release(lease) }))
        assertFalse(success(f.storage.tx { f.repo.release(lease) }))
        assertNull(success(f.storage.tx { f.repo.find(f.owner, lease.slot) }))
        assertEquals(before, f.provenance())
    }

    @Test
    fun actualCompetingClaimsSaturateOnlyGatewaySlotsAndLateReleaseKeepsTheirSuccessor() {
        val f = Fixture()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures =
                (0..7).map {
                    pool.submit<Result<CollaborationSpaceRequestLease?>> {
                        assertTrue(start.await(10, TimeUnit.SECONDS))
                        f.claim()
                    }
                }
            start.countDown()
            val leases = futures.mapNotNull { success(it.get(20, TimeUnit.SECONDS)) }
            assertEquals((1..7).toSet(), leases.map { it.slot }.toSet())
            assertEquals(7, leases.map { it.token }.toSet().size)
            val reserved = requireNotNull(success(f.claim(f.renewal)))
            assertEquals(0, reserved.slot)
            assertNull(success(f.claim(f.renewal)))
            val old = leases.first()
            assertFalse(
                success(f.storage.tx { f.repo.release(old.copy(token = UUID.randomUUID())) })
            )
            assertTrue(success(f.storage.tx { f.repo.release(old) }))
            val next = requireNotNull(success(f.claim()))
            assertEquals(old.slot, next.slot)
            assertNotEquals(old.token, next.token)
            assertFalse(success(f.storage.tx { f.repo.release(old) }))
            assertEquals(next, success(f.storage.tx { f.repo.find(f.owner, next.slot) }))
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun postWriteReadbackFaultsCancellationAndInterruptionRollBackClaimsAndRelease() {
        val f = Fixture()
        for (mode in
            listOf("scope", "slot", "token", "precision", "missing", "cancel", "interrupt")) {
            var wrote = false
            val source =
                object : CollaborationSpaceRequestDataSource by f.source {
                    override fun claim(
                        owner: CollaborationSpaceRequestCoordinatesData,
                        kind: String,
                        gatewayId: UUID?,
                        gatewayVersion: Long?,
                        until: OffsetDateTime,
                    ): List<CollaborationSpaceRequestLeasesRecord> =
                        f.source.claim(owner, kind, gatewayId, gatewayVersion, until).also {
                            wrote = it.isNotEmpty()
                        }

                    override fun find(
                        owner: CollaborationSpaceRequestCoordinatesData,
                        slot: Int,
                        lock: Boolean,
                    ): CollaborationSpaceRequestLeasesRecord? {
                        val row = f.source.find(owner, slot, lock)
                        if (!wrote) return row
                        if (mode == "missing") return null
                        if (mode == "cancel") throw CancellationException("fixture cancellation")
                        if (mode == "interrupt") throw InterruptedException("fixture interruption")
                        return requireNotNull(row).also {
                            when (mode) {
                                "scope" -> it.accountId = UUID.randomUUID()
                                "slot" -> it.slot = 7
                                "token" -> it.token = UUID.randomUUID()
                                "precision" ->
                                    it.expiresAt = requireNotNull(it.expiresAt).plusNanos(1)
                            }
                        }
                    }
                }
            val command = {
                f.claim(repository = StoredCollaborationSpaceRequestRepository(source))
            }
            when (mode) {
                "cancel" -> assertThrows(CancellationException::class.java) { command() }
                "interrupt" -> assertThrows(InterruptedException::class.java) { command() }
                else -> assertInstanceOf(Result.Failed::class.java, command(), mode)
            }
            assertTrue(wrote)
            assertEquals(0, f.count())
        }
        val lease = requireNotNull(success(f.claim()))
        for (cancel in listOf(false, true)) {
            var removed = false
            val source =
                object : CollaborationSpaceRequestDataSource by f.source {
                    override fun release(lease: CollaborationSpaceRequestLeasesRecord): Boolean =
                        f.source.release(lease).also { removed = it }

                    override fun find(
                        owner: CollaborationSpaceRequestCoordinatesData,
                        slot: Int,
                        lock: Boolean,
                    ): CollaborationSpaceRequestLeasesRecord? {
                        if (removed) {
                            if (cancel) throw CancellationException("fixture release cancellation")
                            error("fixture readback failure")
                        }
                        return f.source.find(owner, slot, lock)
                    }
                }
            val command = {
                f.storage.tx { StoredCollaborationSpaceRequestRepository(source).release(lease) }
            }
            if (cancel) assertThrows(CancellationException::class.java) { command() }
            else assertInstanceOf(Result.Failed::class.java, command())
            assertTrue(removed)
            assertEquals(lease, success(f.storage.tx { f.repo.find(f.owner, lease.slot) }))
        }
        assertTrue(success(f.storage.tx { f.repo.release(lease) }))
        val rejected =
            f.storage.tx {
                f.repo.claim(f.position, until()).flatMap {
                    Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rejected"))
                }
            }
        assertInstanceOf(Result.Failed::class.java, rejected)
        assertEquals(0, f.count())
    }

    @Test
    fun expiredWorkerCleanupUsesFiniteDeadlinesAfterPurgeWithoutTouchingLiveOrRetainedFacts() {
        val f = Fixture()
        val live = requireNotNull(success(f.claim()))
        val expired = requireNotNull(success(f.claim(f.renewal, until(2))))
        val worker = StoredCollaborationSpaceRequestRepository(f.source(f.storage.workerSource))
        val transactions = f.storage.transactions(f.storage.workerSource)
        val actor = f.storage.actor.copy(accountId = UUID(0, 0))
        assertEquals(0, success(transactions.run(actor) { worker.clearExpired(Instant.now(), 1) }))
        f.storage.owner.update("delete from native_sessions where id=?", f.owner.nativeSessionId)
        val before = f.provenance()
        val remaining = java.time.Duration.between(Instant.now(), expired.expiresAt).toNanos()
        if (remaining > 0) TimeUnit.NANOSECONDS.sleep(remaining + 20_000_000)
        assertEquals(
            0,
            success(transactions.run(actor) { worker.clearExpired(expired.startedAt, 1) }),
        )
        assertEquals(1, success(transactions.run(actor) { worker.clearExpired(Instant.now(), 1) }))
        assertEquals(
            0,
            success(transactions.run(actor) { worker.clearExpired(Instant.now(), 100) }),
        )
        assertEquals(live, success(f.storage.tx { f.repo.find(f.owner, live.slot) }))
        assertEquals(before, f.provenance())
        assertFalse(success(f.storage.tx { f.repo.release(expired) }))
        assertTrue(success(f.storage.tx { f.repo.release(live) }))
    }

    @Test
    fun invalidBoundsFailBeforeAcquisitionAndMalformedSourceResultsNeverPublishLeases() {
        val f = Fixture()
        var calls = 0
        val forbidden =
            object : CollaborationSpaceRequestDataSource {
                override fun claim(
                    owner: CollaborationSpaceRequestCoordinatesData,
                    kind: String,
                    gatewayId: UUID?,
                    gatewayVersion: Long?,
                    until: OffsetDateTime,
                ): List<CollaborationSpaceRequestLeasesRecord> {
                    calls++
                    error("unexpected fixture I/O")
                }

                override fun find(
                    owner: CollaborationSpaceRequestCoordinatesData,
                    slot: Int,
                    lock: Boolean,
                ): CollaborationSpaceRequestLeasesRecord? {
                    calls++
                    error("unexpected fixture I/O")
                }

                override fun release(lease: CollaborationSpaceRequestLeasesRecord): Boolean {
                    calls++
                    error("unexpected fixture I/O")
                }

                override fun clearExpired(at: OffsetDateTime, limit: Int): Int {
                    calls++
                    error("unexpected fixture I/O")
                }
            }
        val guarded = StoredCollaborationSpaceRequestRepository(forbidden)
        for (invalid in
            listOf(
                f.position.copy(gatewayId = null),
                f.position.copy(gatewayVersion = null),
                f.position.copy(gatewayVersion = 1000000),
                f.position.copy(owner = f.owner.copy(accountId = UUID(0, 0))),
                f.renewal.copy(gatewayId = UUID.randomUUID()),
            )) assertInstanceOf(Result.Failed::class.java, guarded.claim(invalid, until()))
        assertInstanceOf(Result.Failed::class.java, guarded.claim(f.position, until().plusNanos(1)))
        assertInstanceOf(Result.Failed::class.java, guarded.find(f.owner, -1))
        for (limit in listOf(0, 101)) assertInstanceOf(
            Result.Failed::class.java,
            guarded.clearExpired(Instant.now(), limit),
        )
        assertEquals(0, calls)
        val lease = requireNotNull(success(f.claim()))
        val row =
            requireNotNull(
                f.storage
                    .tx {
                        dev.fajar.hris.core.database.safeDatabaseCall {
                            f.source.find(
                                CollaborationSpaceRequestCoordinatesData(
                                    f.owner.spaceId,
                                    f.owner.accountId,
                                    f.owner.deviceId,
                                    f.owner.nativeSessionId,
                                ),
                                lease.slot,
                                false,
                            )
                        }
                    }
                    .let { success(it) }
            )
        val duplicate =
            object : CollaborationSpaceRequestDataSource by forbidden {
                override fun claim(
                    owner: CollaborationSpaceRequestCoordinatesData,
                    kind: String,
                    gatewayId: UUID?,
                    gatewayVersion: Long?,
                    until: OffsetDateTime,
                ) = listOf(row, row)
            }
        assertInstanceOf(
            Result.Failed::class.java,
            StoredCollaborationSpaceRequestRepository(duplicate).claim(f.position, until()),
        )
        for (count in listOf(-1, 2)) {
            val invalid =
                object : CollaborationSpaceRequestDataSource by forbidden {
                    override fun clearExpired(at: OffsetDateTime, limit: Int) = count
                }
            assertInstanceOf(
                Result.Failed::class.java,
                StoredCollaborationSpaceRequestRepository(invalid).clearExpired(Instant.now(), 1),
            )
        }
        assertEquals(0, calls)
    }

    private inner class Fixture {
        val storage = SpaceGatewayPostgresFixture(postgres).also { it.migrate() }
        val owner =
            CollaborationSpaceRequestOwner(
                storage.space,
                storage.account,
                storage.device,
                storage.parent,
            )
        val position =
            CollaborationSpaceRequestPosition(
                owner,
                CollaborationSpaceRequestKind.GATEWAY,
                storage.session.id,
                0,
            )
        val renewal =
            CollaborationSpaceRequestPosition(owner, CollaborationSpaceRequestKind.PROVIDER_RENEWAL)
        val source = source(storage.runtimeSource)
        val repo = StoredCollaborationSpaceRequestRepository(source)

        init {
            success(
                storage.tx {
                    storage.repo.create(
                        storage.session,
                        storage.operation,
                        storage.at.plusSeconds(30),
                    )
                }
            )
            Flyway.configure().dataSource(storage.source).target("127").load().migrate()
            storage.owner.execute(
                "grant select,insert,update,delete on collaboration_space_request_leases to space_gateway_fixture,space_gateway_worker"
            )
        }

        fun source(data: DataSource) =
            PostgresCollaborationSpaceRequestDataSource(
                DSL.using(TransactionAwareDataSourceProxy(data), SQLDialect.POSTGRES)
            )

        fun claim(
            position: CollaborationSpaceRequestPosition = this.position,
            deadline: Instant = until(),
            repository: StoredCollaborationSpaceRequestRepository = repo,
        ) = storage.tx { repository.claim(position, deadline) }

        fun count() =
            storage.owner.queryForObject(
                "select count(*) from collaboration_space_request_leases",
                Int::class.java,
            )

        fun provenance() =
            listOf(
                    "collaboration_space_devices",
                    "collaboration_space_provider_sessions",
                    "collaboration_space_gateway_sessions",
                )
                .associateWith { storage.owner.queryForList("select * from $it") }
    }

    private fun until(seconds: Long = 30) =
        Instant.now().plusSeconds(seconds).truncatedTo(ChronoUnit.MICROS)

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result) {
            (result as? Result.Failed)?.failure?.code ?: "Unexpected result"
        }
        return (result as Result.Success).value
    }
}
