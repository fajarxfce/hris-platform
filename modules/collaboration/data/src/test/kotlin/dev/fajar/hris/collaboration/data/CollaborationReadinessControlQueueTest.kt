package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.domain.*
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
class CollaborationReadinessControlQueueTest {
    companion object {
        @Container
        @JvmStatic
        val postgres =
            PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))
        lateinit var owner: JdbcTemplate

        @BeforeAll
        @JvmStatic
        fun migrate() {
            val source =
                DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            owner = JdbcTemplate(source)
            Flyway.configure().dataSource(source).target("116.2").load().migrate()
            for (role in
                listOf("readiness_control_api_fixture", "readiness_control_worker_fixture")) {
                owner.execute("create role $role login password 'readiness-fixture-only'")
                owner.execute("grant usage on schema public to $role")
                owner.execute(
                    "grant select,insert,update,delete on all tables in schema public to $role"
                )
            }
            owner.execute("grant hris_worker_capability to readiness_control_worker_fixture")
            val original = Fixture(1)
            success(original.publish())
            val before =
                owner.queryForList(
                    "select row_to_json(k)::text as snapshot from collaboration_device_key_publications k order by company_id,device_id"
                )
            Flyway.configure().dataSource(source).target("116.3").load().migrate()
            assertEquals(
                before,
                owner.queryForList(
                    "select row_to_json(k)::text as snapshot from collaboration_device_key_publications k order by company_id,device_id"
                ),
            )
            assertEquals(
                0,
                owner.queryForObject(
                    "select count(*) from collaboration_readiness_controls",
                    Int::class.java,
                ),
            )
            val legacy = Fixture(0)
            success(legacy.publish())
            val oldControl =
                owner.queryForList(
                    "select to_jsonb(c)::text as snapshot from collaboration_readiness_controls c"
                )
            Flyway.configure().dataSource(source).target("116.4").load().migrate()
            assertEquals(
                oldControl,
                owner.queryForList(
                    "select (to_jsonb(c)-'publication_revision')::text as snapshot from collaboration_readiness_controls c"
                ),
            )
            assertEquals(
                1,
                owner.queryForObject(
                    "select count(*) from collaboration_readiness_controls where publication_revision is null",
                    Int::class.java,
                ),
            )
            assertEquals(
                0,
                owner.queryForObject(
                    "select count(*) from collaboration_readiness_revisions",
                    Int::class.java,
                ),
            )
            for (role in
                listOf("readiness_control_api_fixture", "readiness_control_worker_fixture")) {
                owner.execute(
                    "grant select,insert,update,delete on all tables in schema public to $role"
                )
            }
            assertTrue(success(Runtime(true).claim()).isEmpty())
            owner.execute("truncate collaboration_readiness_controls")
            assertEquals(
                2,
                owner.queryForObject(
                    "select count(*) from collaboration_readiness_wakes",
                    Int::class.java,
                ),
            )
            owner.execute("truncate collaboration_readiness_wakes")
        }

        private fun <T> success(result: Result<T>): T {
            assertInstanceOf(Result.Success::class.java, result)
            return (result as Result.Success).value
        }
    }

    @AfterEach
    fun isolateQueue() {
        // Fixture owner only. Runtime roles have no TRUNCATE authority.
        owner.execute(
            "truncate collaboration_readiness_controls,collaboration_readiness_wakes,collaboration_push_hints"
        )
    }

    @Test
    fun publicationRevisionFollowsCommitOrderEvenWhenTimestampsAreReversedAndRollbackLeavesNoWake() {
        for (rollback in listOf(false, true)) {
            val f = Fixture(0)
            val older = Participant(f.company, f.now.minusSeconds(3))
            val held = CountDownLatch(1)
            val release = CountDownLatch(1)
            val secondEntering = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val first =
                    pool.submit<Result<Unit>> {
                        f.publish(
                            rollback = rollback,
                            beforeCommit = {
                                held.countDown()
                                check(release.await(5, TimeUnit.SECONDS))
                            },
                        )
                    }
                assertTrue(held.await(5, TimeUnit.SECONDS))
                val second =
                    pool.submit<Result<Unit>> {
                        older.runtime.run(older.actor) {
                            older.runtime.sql.update(
                                "update collaboration_device_key_publications set version=1,published_at=? where company_id=? and device_id=?",
                                f.now.minusSeconds(2).atOffset(ZoneOffset.UTC),
                                f.company,
                                older.device,
                            )
                            secondEntering.countDown()
                            older.runtime.sql.update(
                                "insert into outbox_events(id,company_id,actor_id,event_type,resource_id,payload) values(?,?,?,'collaboration.device_keys_published',?,'{}')",
                                UUID.randomUUID(),
                                f.company,
                                older.account,
                                older.device,
                            )
                            Result.Success(Unit)
                        }
                    }
                assertTrue(secondEntering.await(5, TimeUnit.SECONDS))
                assertThrows(TimeoutException::class.java) {
                    second.get(150, TimeUnit.MILLISECONDS)
                }
                assertEquals(0, f.count("collaboration_readiness_controls"))
                assertEquals(0, f.count("collaboration_readiness_revisions"))
                release.countDown()
                val firstResult = first.get(5, TimeUnit.SECONDS)
                if (rollback) assertInstanceOf(Result.Failed::class.java, firstResult)
                else success(firstResult)
                success(second.get(5, TimeUnit.SECONDS))
                val rows =
                    owner.queryForList(
                        "select publisher_account_id,publication_revision,publication_cutoff from collaboration_readiness_controls where company_id=? order by publication_revision",
                        f.company,
                    )
                assertEquals(if (rollback) 1 else 2, rows.size)
                assertEquals(older.account, rows.last()["publisher_account_id"])
                assertEquals(if (rollback) 1L else 2L, rows.last()["publication_revision"])
                if (!rollback) {
                    assertEquals(f.publisher.account, rows.first()["publisher_account_id"])
                    assertTrue(
                        (rows.first()["publication_cutoff"] as java.sql.Timestamp).after(
                            rows.last()["publication_cutoff"] as java.sql.Timestamp
                        )
                    )
                    success(f.repeatEvent())
                    assertEquals(
                        2L,
                        owner.queryForObject(
                            "select revision from collaboration_readiness_revisions where company_id=?",
                            Long::class.java,
                            f.company,
                        ),
                    )
                    assertEquals(
                        1L,
                        owner.queryForObject(
                            "select publication_revision from collaboration_readiness_controls where company_id=? and publisher_device_id=?",
                            Long::class.java,
                            f.company,
                            f.publisher.device,
                        ),
                    )
                }
            } finally {
                release.countDown()
                pool.shutdownNow()
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
            }
        }
    }

    @Test
    fun runtimeCannotReadOrAlterThePublicationCounterEvenWithBroadTableGrants() {
        val f = Fixture(0)
        success(f.publish())
        val worker = Runtime(true)
        success(
            worker.run(f.publisher.actor) {
                assertEquals(
                    0,
                    worker.sql.queryForObject(
                        "select count(*) from collaboration_readiness_revisions",
                        Int::class.java,
                    ),
                )
                assertEquals(
                    0,
                    worker.sql.update(
                        "update collaboration_readiness_revisions set revision=revision+1"
                    ),
                )
                Result.Success(Unit)
            }
        )
        assertEquals(
            1L,
            owner.queryForObject(
                "select revision from collaboration_readiness_revisions where company_id=?",
                Long::class.java,
                f.company,
            ),
        )
    }

    @Test
    fun controlsPagePublishedDevicesWithoutPushAndCannotAdvanceTheFcmCheckpoint() {
        val f = Fixture(9)
        success(f.publish())
        val worker = Runtime(true)
        val fcm =
            StoredCollaborationReadinessWakeRepository(
                PostgresCollaborationReadinessWakeDataSource(worker.jooq)
            )
        val fcmLease = success(worker.run { fcm.claim(UUID.randomUUID(), 1) }).single()
        val lease = success(worker.claim()).single()
        assertEquals(fcmLease.eventId, lease.eventId)
        assertNotEquals(fcmLease.token, lease.token)
        var checkpoint =
            success(worker.run(f.publisher.actor) { worker.wakes.find(lease, false) })!!
        f.addPeer(f.now.plusSeconds(1))
        val page = success(worker.run(f.publisher.actor) { worker.wakes.targets(checkpoint, 8) })
        assertEquals(8, page.size)
        assertEquals(0, f.count("collaboration_push_bindings"))
        val first = page.first()
        val scope = f.peers.single { it.device == first.position.deviceId }.actor
        assertInstanceOf(
            Result.Failed::class.java,
            worker.run(scope) {
                assertTrue(success(worker.wakes.advance(checkpoint, first.position)))
                Result.Failed(Failure(FailureKind.CONFLICT, "fixture_rollback"))
            },
        )
        assertEquals(
            checkpoint,
            success(worker.run(f.publisher.actor) { worker.wakes.find(lease, false) }),
        )
        for (target in page) {
            val actor = f.peers.single { it.device == target.position.deviceId }.actor
            assertTrue(
                success(worker.run(actor) { worker.wakes.advance(checkpoint, target.position) })
            )
            checkpoint = checkpoint.copy(after = target.position)
        }
        assertTrue(
            success(
                worker.run(f.publisher.actor) {
                    worker.wakes.release(checkpoint, false, Instant.now())
                }
            )
        )
        val next = success(Runtime(true).claim()).single()
        checkpoint = success(worker.run(f.publisher.actor) { worker.wakes.find(next, false) })!!
        assertEquals(
            1,
            success(worker.run(f.publisher.actor) { worker.wakes.targets(checkpoint, 8) }).size,
        )
        assertNull(success(worker.run(f.publisher.actor) { fcm.find(fcmLease, false) })!!.after)
        assertEquals(0, f.count("collaboration_push_hints"))
        assertFalse(
            owner.queryForObject(
                "select has_column_privilege('hris_collaboration_readiness_reader','collaboration_device_key_publications','fingerprint','SELECT')",
                Boolean::class.java,
            )!!
        )
    }

    @Test
    fun independentControlClaimsHaveAGlobalCapacityAndNoHiddenRetryReset() {
        repeat(20) { success(Fixture(0).publish()) }
        val worker = Runtime(true)
        val leases = (1..4).flatMap { success(worker.claim(4)) }
        assertEquals(16, leases.size)
        assertEquals(16, leases.map { it.eventId }.distinct().size)
        assertTrue(success(Runtime(true).claim()).isEmpty())
    }

    @Test
    fun rolledBackPublicationProducesNeitherReadinessNorExecutableWake() {
        val f = Fixture(1)
        assertInstanceOf(Result.Failed::class.java, f.publish(rollback = true))
        assertEquals(0, f.count("collaboration_readiness_controls"))
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
        assertEquals(1, f.count("collaboration_readiness_controls"))
        success(f.repeatEvent())
        assertEquals(1, f.count("collaboration_readiness_controls"))
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
            assertEquals(0, f.count("collaboration_readiness_controls"))
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
                    "select attempts from collaboration_readiness_controls where event_id=?",
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
                        "select count(*) from collaboration_readiness_controls",
                        Int::class.java,
                    ),
                )
                assertEquals(
                    0,
                    api.sql.update("update collaboration_readiness_controls set attempts=0"),
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
                    "select has_column_privilege('hris_collaboration_readiness_capture','collaboration_readiness_controls',?,'SELECT')",
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
                "select has_table_privilege('hris_collaboration_readiness_capture','collaboration_readiness_controls','UPDATE,DELETE')",
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
                            "select count(*) from collaboration_device_key_publications",
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
        assertEquals(1, f.count("collaboration_readiness_controls"))
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
                "publication_revision=publication_revision+1",
            )) {
            assertInstanceOf(
                Result.Failed::class.java,
                worker.run(f.publisher.actor) {
                    worker.sql.update(
                        "update collaboration_readiness_controls set $assignment where event_id=?",
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

    private class Runtime(worker: Boolean = false) {
        private val source =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                if (worker) "readiness_control_worker_fixture" else "readiness_control_api_fixture",
                "readiness-fixture-only",
            )
        val sql = JdbcTemplate(source)
        val tx = PostgresTransactionRunner(JdbcTransactionManager(source), sql)
        val jooq = DSL.using(TransactionAwareDataSourceProxy(source), SQLDialect.POSTGRES)
        val wakes =
            StoredCollaborationReadinessControlQueueRepository(
                PostgresCollaborationReadinessControlQueueDataSource(jooq)
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
            owner.update(
                "insert into collaboration_device_key_publications(company_id,device_id,account_id,fingerprint,version,reserved_at) values(?,?,?,repeat('a',64),0,?)",
                company,
                device,
                account,
                at.minusSeconds(1).atOffset(ZoneOffset.UTC),
            )
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
        }

        fun addPeer(at: Instant, conversationCreatedAt: Instant = at): Participant {
            val peer = Participant(company, at)
            owner.update(
                "update collaboration_device_key_publications set version=1,published_at=? where company_id=? and device_id=?",
                at.atOffset(ZoneOffset.UTC),
                company,
                peer.device,
            )
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
