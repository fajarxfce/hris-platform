package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.usecases.FanOutCollaborationReadiness
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.crypto.parseIdentityKeyring
import dev.fajar.hris.identity.data.datasources.JceIdentityTokenDataSource
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.*
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.*
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationReadinessWakeTest {
    companion object {
        @Container
        @JvmStatic
        val postgres =
            PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))
        lateinit var owner: JdbcTemplate
        private val keyring =
            parseIdentityKeyring(
                "fixture",
                "fixture:" + Base64.getEncoder().encodeToString(ByteArray(32) { 31 }),
            )

        @BeforeAll
        @JvmStatic
        fun migrate() {
            val source =
                DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            owner = JdbcTemplate(source)
            Flyway.configure().dataSource(source).target("116.1").load().migrate()
            for (role in listOf("readiness_api_fixture", "readiness_worker_fixture")) {
                owner.execute("create role $role login password 'readiness-fixture-only'")
                owner.execute("grant usage on schema public to $role")
                owner.execute(
                    "grant select,insert,update,delete on all tables in schema public to $role"
                )
            }
            owner.execute("grant hris_worker_capability to readiness_worker_fixture")
            val original = Fixture(1)
            success(original.publish())
            val before =
                owner.queryForList(
                    "select row_to_json(k)::text as snapshot from collaboration_device_key_publications k order by company_id,device_id"
                )
            Flyway.configure().dataSource(source).target("116.2").load().migrate()
            assertEquals(
                before,
                owner.queryForList(
                    "select row_to_json(k)::text as snapshot from collaboration_device_key_publications k order by company_id,device_id"
                ),
            )
            assertEquals(
                0,
                owner.queryForObject(
                    "select count(*) from collaboration_readiness_wakes",
                    Int::class.java,
                ),
            )
            for (role in listOf("readiness_api_fixture", "readiness_worker_fixture")) {
                owner.execute(
                    "grant select,insert,update,delete on all tables in schema public to $role"
                )
            }
        }

        private fun <T> success(result: Result<T>): T {
            assertInstanceOf(Result.Success::class.java, result)
            return (result as Result.Success).value
        }
    }

    @AfterEach
    fun isolateQueue() {
        // Fixture owner only. Runtime roles have no TRUNCATE authority.
        owner.execute("truncate collaboration_readiness_wakes,collaboration_push_hints")
    }

    @Test
    fun rolledBackPublicationProducesNeitherReadinessNorExecutableWake() {
        val f = Fixture(1)
        assertInstanceOf(Result.Failed::class.java, f.publish(rollback = true))
        assertEquals(0, f.count("collaboration_readiness_wakes"))
        assertEquals(0, f.count("outbox_events"))
        assertEquals(
            0L,
            owner.queryForObject(
                "select version from collaboration_device_key_publications where device_id=?",
                Long::class.java,
                f.publisher.device,
            ),
        )
        success(f.publish())
        assertEquals(1, f.count("collaboration_readiness_wakes"))
        success(f.repeatEvent())
        assertEquals(1, f.count("collaboration_readiness_wakes"))
        assertEquals(2, f.count("outbox_events"))
    }

    @Test
    fun workerCannotObservePublicationBeforeItsOwningTransactionCommits() {
        val f = Fixture(1)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        try {
            val publishing =
                pool.submit<Result<Unit>> {
                    f.publish(
                        beforeCommit = {
                            entered.countDown()
                            check(release.await(5, TimeUnit.SECONDS))
                        }
                    )
                }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val worker = Runtime(true)
            assertTrue(success(worker.claim()).isEmpty())
            assertEquals(0, f.count("collaboration_readiness_wakes"))
            release.countDown()
            success(publishing.get(5, TimeUnit.SECONDS))
            assertEquals(1, success(worker.claim()).size)
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun eightClaimsWithoutCheckpointProgressExhaustTheDurableAttemptBudget() {
        val f = Fixture(1)
        success(f.publish())
        val worker = Runtime(true)
        repeat(8) { attempt ->
            val lease = success(worker.claim()).single()
            val checkpoint =
                success(worker.run(f.publisher.actor) { worker.wakes.find(lease, false) })!!
            assertEquals(
                attempt + 1,
                owner.queryForObject(
                    "select attempts from collaboration_readiness_wakes where event_id=?",
                    Int::class.java,
                    lease.eventId,
                ),
            )
            // End this owned lease without progress; neither release nor a process restart resets
            // it.
            assertTrue(
                success(
                    worker.run(f.publisher.actor) {
                        worker.wakes.release(checkpoint, false, Instant.now())
                    }
                )
            )
        }
        assertTrue(success(Runtime(true).claim()).isEmpty())
        assertEquals(0, f.count("collaboration_push_hints"))
    }

    @Test
    fun publicationCaptureDoesNotGrantItsCallerWorkerMetadataOrMutation() {
        val f = Fixture(1)
        success(f.publish())
        success(f.repeatEvent())
        val api = f.publisher.runtime
        success(
            api.run(f.publisher.actor) {
                assertEquals(
                    0,
                    api.sql.queryForObject(
                        "select count(*) from collaboration_readiness_wakes",
                        Int::class.java,
                    ),
                )
                assertEquals(
                    0,
                    api.sql.update("update collaboration_readiness_wakes set attempts=0"),
                )
                Result.Success(Unit)
            }
        )
        for (column in
            listOf(
                "lease_token",
                "lease_owner",
                "lease_until",
                "after_account_id",
                "after_device_id",
            )) {
            assertFalse(
                owner.queryForObject(
                    "select has_column_privilege('hris_collaboration_readiness_capture','collaboration_readiness_wakes',?,'SELECT')",
                    Boolean::class.java,
                    column,
                )!!
            )
        }
        assertFalse(
            owner.queryForObject(
                "select has_column_privilege('hris_collaboration_readiness_capture','outbox_events','payload','SELECT')",
                Boolean::class.java,
            )!!
        )
        assertFalse(
            owner.queryForObject(
                "select has_table_privilege('hris_collaboration_readiness_capture','collaboration_readiness_wakes','UPDATE,DELETE')",
                Boolean::class.java,
            )!!
        )
        assertEquals(1, success(Runtime(true).claim()).size)
    }

    @Test
    fun targetProjectionNeedsWorkerSessionAndPublisherCompanyEvenWithBroadTableGrants() {
        val f = Fixture(2)
        success(f.publish())
        val worker = Runtime(true)
        val lease = success(worker.claim()).single()
        val checkpoint =
            success(worker.run(f.publisher.actor) { worker.wakes.find(lease, false) })!!
        assertEquals(
            2,
            success(worker.run(f.publisher.actor) { worker.wakes.targets(checkpoint, 8) }).size,
        )
        val api = Runtime()
        assertTrue(
            success(api.run(f.publisher.actor) { api.wakes.targets(checkpoint, 8) }).isEmpty()
        )
        assertTrue(
            success(worker.run(f.peers[0].actor) { worker.wakes.targets(checkpoint, 8) }).isEmpty()
        )
        assertTrue(
            success(
                    worker.run(f.publisher.actor.copy(companyId = UUID.randomUUID())) {
                        worker.wakes.targets(checkpoint, 8)
                    }
                )
                .isEmpty()
        )
        assertInstanceOf(Result.Failed::class.java, api.claim())
        assertEquals(
            1,
            success(
                api.run(f.publisher.actor) {
                    Result.Success(
                        api.sql.queryForObject(
                            "select count(*) from collaboration_push_bindings",
                            Int::class.java,
                        )
                    )
                }
            ),
        )
    }

    @Test
    fun wrongPublicationOwnerOrDeviceIsRejectedInsideOriginalTransaction() {
        val f = Fixture(1)
        success(f.publish())
        val api = Runtime()
        for ((actor, device) in
            listOf(
                f.peers[0].actor to f.publisher.device,
                f.publisher.actor to f.peers[0].device,
                f.publisher.actor.copy(companyId = UUID.randomUUID()) to f.publisher.device,
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                api.run(actor) {
                    api.sql.update(
                        "insert into outbox_events(id,company_id,actor_id,event_type,resource_id,payload) values(?,?,?,'collaboration.device_keys_published',?,'{}')",
                        UUID.randomUUID(),
                        actor.companyId,
                        actor.accountId,
                        device,
                    )
                    Result.Success(Unit)
                },
            )
        }
        assertEquals(1, f.count("outbox_events"))
        assertEquals(1, f.count("collaboration_readiness_wakes"))
    }

    @Test
    fun queueAppendAndCursorRollbackTogetherAndCompletedReplayCannotAdvanceDeliveredHint() {
        val f = Fixture(1)
        success(f.publish())
        val worker = Runtime(true)
        val lease = success(worker.claim()).single()
        val checkpoint = success(worker.run(f.publisher.actor) { worker.wakes.find(lease, true) })!!
        val peer = f.peers.single()
        val position = CollaborationReadinessPosition(peer.account, peer.device)
        assertInstanceOf(
            Result.Failed::class.java,
            worker.run(peer.actor) {
                success(worker.pushes.append(peer.binding, null, 300))
                assertTrue(success(worker.wakes.advance(checkpoint, position)))
                Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rollback"))
            },
        )
        assertEquals(0, f.count("collaboration_push_hints"))
        assertNull(
            success(worker.run(f.publisher.actor) { worker.wakes.find(lease, false) })!!.after
        )
        success(worker.fanOut.fanOut(lease))
        assertEquals(1, f.count("collaboration_push_hints"))
        val head =
            success(
                worker.run(peer.actor) { worker.pushes.find(f.company, peer.account, peer.device) }
            )!!
        val delivered =
            success(worker.run { worker.pushes.claim(UUID.randomUUID(), 1, 30, 8) }).single()
        assertTrue(
            success(
                worker.run(peer.actor) { worker.pushes.finish(requireNotNull(delivered.lease)) }
            )
        )
        assertInstanceOf(Result.Failed::class.java, worker.fanOut.fanOut(lease))
        success(f.repeatEvent())
        assertTrue(success(worker.claim()).isEmpty())
        val after =
            success(
                worker.run(peer.actor) { worker.pushes.find(f.company, peer.account, peer.device) }
            )!!
        assertEquals(head.revision, after.revision)
        assertEquals(after.revision, after.deliveredRevision)
    }

    @Test
    fun appendFailureDoesNotCommitAPreviouslyMovedCheckpoint() {
        val f = Fixture(1)
        success(f.publish())
        val worker = Runtime(true)
        val lease = success(worker.claim()).single()
        val checkpoint = success(worker.run(f.publisher.actor) { worker.wakes.find(lease, true) })!!
        val peer = f.peers.single()
        assertInstanceOf(
            Result.Failed::class.java,
            worker.run(peer.actor) {
                assertTrue(
                    success(
                        worker.wakes.advance(
                            checkpoint,
                            CollaborationReadinessPosition(peer.account, peer.device),
                        )
                    )
                )
                worker.pushes
                    .append(peer.binding.copy(nativeSessionId = UUID.randomUUID()), null, 300)
                    .map { Unit }
            },
        )
        assertNull(
            success(worker.run(f.publisher.actor) { worker.wakes.find(lease, false) })!!.after
        )
        assertEquals(0, f.count("collaboration_push_hints"))
    }

    @Test
    fun finitePagesFenceOldOwnerAndExcludeBindingsCreatedAfterPublication() {
        val f = Fixture(9)
        success(f.publish())
        val afterPublication = Instant.now().truncatedTo(ChronoUnit.MICROS)
        assertTrue(afterPublication > f.now)
        // Independently exclude a new binding for an old pair and a new pair
        // whose binding already existed. Either cutoff must constrain fanout.
        f.addPeer(afterPublication, conversationCreatedAt = f.now.minusSeconds(1))
        f.addPeer(f.now.minusSeconds(1), conversationCreatedAt = afterPublication)
        val worker = Runtime(true)
        val first = success(worker.claim()).single()
        success(worker.fanOut.fanOut(first))
        assertEquals(8, f.count("collaboration_push_hints"))
        assertInstanceOf(Result.Failed::class.java, worker.fanOut.fanOut(first))
        val next = success(worker.claim()).single()
        assertNotEquals(first.token, next.token)
        success(worker.fanOut.fanOut(next))
        assertEquals(9, f.count("collaboration_push_hints"))
        assertTrue(success(worker.claim()).isEmpty())
    }

    @Test
    fun concurrentClaimsNeverSharePublicationOwnership() {
        repeat(6) { success(Fixture(0).publish()) }
        val pool = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val results =
                (1..2).map {
                    pool.submit<List<CollaborationReadinessLease>> {
                        val worker = Runtime(true)
                        ready.countDown()
                        check(start.await(5, TimeUnit.SECONDS))
                        success(worker.claim(4))
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val leases = results.flatMap { it.get(10, TimeUnit.SECONDS) }
            assertTrue(leases.size in 4..6)
            assertEquals(leases.size, leases.map { it.eventId }.distinct().size)
            assertEquals(leases.size, leases.map { it.token }.distinct().size)
        } finally {
            start.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun activeLeaseRejectsDeadlineExtensionAttemptResetAndForeignToken() {
        val f = Fixture(1)
        success(f.publish())
        val worker = Runtime(true)
        val lease = success(worker.claim()).single()
        for (assignment in
            listOf(
                "lease_until=lease_until+interval '1 second'",
                "attempts=0",
                "lease_token=gen_random_uuid()",
                "publisher_account_id=gen_random_uuid()",
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                worker.run(f.publisher.actor) {
                    worker.sql.update(
                        "update collaboration_readiness_wakes set $assignment where event_id=?",
                        lease.eventId,
                    )
                    Result.Success(Unit)
                },
            )
        }
        val checkpoint =
            success(worker.run(f.publisher.actor) { worker.wakes.find(lease, false) })!!
        assertEquals(lease, checkpoint.lease)
        assertNull(checkpoint.after)
        assertNull(
            success(
                worker.run(f.publisher.actor) {
                    worker.wakes.find(lease.copy(token = UUID.randomUUID()), false)
                }
            )
        )
    }

    @Test
    fun expiredLeaseCanBeReclaimedButItsLateOwnerCannotMoveTheCheckpoint() {
        val f = Fixture(1)
        success(f.publish())
        val worker = Runtime(true)
        val event =
            owner.queryForObject(
                "select event_id from collaboration_readiness_wakes where company_id=?",
                UUID::class.java,
                f.company,
            )!!
        val ownerId = UUID.randomUUID()
        val token = UUID.randomUUID()
        val until =
            success(
                worker.run(f.publisher.actor) {
                    Result.Success(
                        worker.sql
                            .queryForObject(
                                "update collaboration_readiness_wakes set lease_owner=?,lease_token=?,lease_until=clock_timestamp()+interval '1 second',attempts=1 where event_id=? returning lease_until",
                                java.sql.Timestamp::class.java,
                                ownerId,
                                token,
                                event,
                            )!!
                            .toInstant()
                    )
                }
            )
        val expired =
            CollaborationReadinessLease(
                event,
                f.company,
                f.publisher.account,
                f.publisher.device,
                ownerId,
                token,
                until,
            )
        val checkpoint =
            success(worker.run(f.publisher.actor) { worker.wakes.find(expired, false) })!!
        val waitMillis = Duration.between(Instant.now(), until).toMillis().coerceAtLeast(0) + 150
        assertTrue(waitMillis <= 1_200)
        TimeUnit.MILLISECONDS.sleep(waitMillis)
        val current = success(worker.claim()).single()
        assertEquals(event, current.eventId)
        assertNotEquals(token, current.token)
        assertNull(success(worker.run(f.publisher.actor) { worker.wakes.find(expired, false) }))
        assertFalse(
            success(
                worker.run(f.peers[0].actor) {
                    worker.wakes.advance(
                        checkpoint,
                        CollaborationReadinessPosition(f.peers[0].account, f.peers[0].device),
                    )
                }
            )
        )
        assertFalse(
            success(
                worker.run(f.publisher.actor) {
                    worker.wakes.release(checkpoint, true, Instant.now())
                }
            )
        )
        success(worker.fanOut.fanOut(current))
        assertEquals(1, f.count("collaboration_push_hints"))
    }

    @Test
    fun interruptionRollsBackHintAndTargetCheckpoint() {
        val f = Fixture(1)
        success(f.publish())
        val worker = Runtime(true)
        val lease = success(worker.claim()).single()
        val checkpoint =
            success(worker.run(f.publisher.actor) { worker.wakes.find(lease, false) })!!
        val peer = f.peers.single()
        assertThrows(InterruptedException::class.java) {
            worker.run(peer.actor) {
                success(worker.pushes.append(peer.binding, null, 300))
                assertTrue(
                    success(
                        worker.wakes.advance(
                            checkpoint,
                            CollaborationReadinessPosition(peer.account, peer.device),
                        )
                    )
                )
                throw InterruptedException()
                @Suppress("UNREACHABLE_CODE") Result.Success(Unit)
            }
        }
        assertNull(
            success(worker.run(f.publisher.actor) { worker.wakes.find(lease, false) })!!.after
        )
        assertEquals(0, f.count("collaboration_push_hints"))
    }

    private class Runtime(worker: Boolean = false) {
        private val source =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                if (worker) "readiness_worker_fixture" else "readiness_api_fixture",
                "readiness-fixture-only",
            )
        val sql = JdbcTemplate(source)
        val tx = PostgresTransactionRunner(JdbcTransactionManager(source), sql)
        val jooq = DSL.using(TransactionAwareDataSourceProxy(source), SQLDialect.POSTGRES)
        val wakes =
            StoredCollaborationReadinessWakeRepository(
                PostgresCollaborationReadinessWakeDataSource(jooq)
            )
        val pushes =
            StoredCollaborationPushQueueRepository(PostgresCollaborationPushQueueDataSource(jooq))
        val bindings =
            EncryptedCollaborationPushBindingRepository(
                PostgresCollaborationPushBindingDataSource(jooq),
                JceIdentityTokenDataSource(keyring),
            )
        val fanOut =
            FanOutCollaborationReadiness(
                wakes,
                bindings,
                pushes,
                tx,
                CollaborationServicePolicy(true, true),
                CollaborationMatrixPolicy(true, "matrix.example.test"),
                Clock.systemUTC(),
            )
        val actor = Actor(UUID(0, 0), null, emptySet(), Instant.now(), UUID.randomUUID())

        fun <T> run(actor: Actor = this.actor, work: () -> Result<T>): Result<T> =
            tx.run(actor, work)

        fun claim(limit: Int = 1): Result<List<CollaborationReadinessLease>> = run {
            wakes.claim(UUID.randomUUID(), limit)
        }
    }

    private class Participant(val company: UUID, at: Instant) {
        val account = UUID.randomUUID()
        val parent = UUID.randomUUID()
        val device = UUID.randomUUID()
        val actor = Actor(account, company, emptySet(), at, UUID.randomUUID())
        val runtime = Runtime()
        val binding: CollaborationPushBinding

        init {
            val before = at.minusSeconds(120).atOffset(ZoneOffset.UTC)
            owner.update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional readiness participant')",
                account,
                "$account@example.test",
            )
            val hash = parent.toString().replace("-", "").repeat(2)
            owner.update(
                "insert into native_sessions(id,account_id,refresh_hash,access_hash,authenticated_at,expires_at,access_expires_at,created_at,rotated_at) values(?,?,?,?,?,?,?,?,?)",
                parent,
                account,
                hash,
                hash,
                before,
                at.plusSeconds(86400).atOffset(ZoneOffset.UTC),
                at.plusSeconds(900).atOffset(ZoneOffset.UTC),
                before,
                before,
            )
            owner.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional readiness device','PROVISIONING',0,?,?)",
                company,
                device,
                account,
                before,
                before,
            )
            owner.update(
                "insert into collaboration_device_sessions(company_id,device_id,account_id,native_session_id,bound_at) values(?,?,?,?,?)",
                company,
                device,
                account,
                parent,
                before,
            )
            owner.update(
                "update collaboration_devices set state='ACTIVE',version=1 where company_id=? and id=?",
                company,
                device,
            )
            val reserved =
                CollaborationPushBinding(
                    company,
                    account,
                    device,
                    parent,
                    0,
                    at.minusSeconds(1),
                    null,
                )
            success(runtime.run(actor) { runtime.bindings.reserve(reserved) })
            assertTrue(success(runtime.run(actor) { runtime.bindings.publish(reserved, at) }))
            binding = reserved.copy(version = 1, publishedAt = at)
        }
    }

    private class Fixture(peers: Int) {
        val company = UUID.randomUUID()
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val publisher: Participant
        val peers = mutableListOf<Participant>()

        init {
            owner.update(
                "insert into companies(id,code,name,timezone) values(?,?,'Fictional readiness company','UTC')",
                company,
                company.toString().replace("-", ""),
            )
            publisher = Participant(company, now.minusSeconds(2))
            repeat(peers) { addPeer(now.minusSeconds(1)) }
            owner.update(
                "insert into collaboration_device_key_publications(company_id,device_id,account_id,fingerprint,version,reserved_at) values(?,?,?,repeat('a',64),0,?)",
                company,
                publisher.device,
                publisher.account,
                now.minusSeconds(1).atOffset(ZoneOffset.UTC),
            )
        }

        fun addPeer(at: Instant, conversationCreatedAt: Instant = at): Participant {
            val peer = Participant(company, at)
            val pair = listOf(publisher.account, peer.account).sortedBy { it.toString() }
            owner.update(
                "insert into collaboration_direct_conversations(company_id,id,first_account_id,second_account_id,created_by,created_at) values(?,?,?,?,?,?)",
                company,
                UUID.randomUUID(),
                pair[0],
                pair[1],
                publisher.account,
                conversationCreatedAt.atOffset(ZoneOffset.UTC),
            )
            peers.add(peer)
            return peer
        }

        fun publish(rollback: Boolean = false, beforeCommit: () -> Unit = {}): Result<Unit> =
            publisher.runtime.run(publisher.actor) {
                publisher.runtime.sql.update(
                    "update collaboration_device_key_publications set version=1,published_at=? where company_id=? and device_id=?",
                    now.atOffset(ZoneOffset.UTC),
                    company,
                    publisher.device,
                )
                appendEvent()
                beforeCommit()
                if (rollback) Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rollback"))
                else Result.Success(Unit)
            }

        fun repeatEvent(): Result<Unit> =
            publisher.runtime.run(publisher.actor) {
                appendEvent()
                Result.Success(Unit)
            }

        private fun appendEvent() {
            publisher.runtime.sql.update(
                "insert into outbox_events(id,company_id,actor_id,event_type,resource_id,payload) values(?,?,?,'collaboration.device_keys_published',?,'{}')",
                UUID.randomUUID(),
                company,
                publisher.account,
                publisher.device,
            )
        }

        fun count(table: String): Int =
            owner.queryForObject(
                "select count(*) from $table where company_id=?",
                Int::class.java,
                company,
            )!!
    }
}
