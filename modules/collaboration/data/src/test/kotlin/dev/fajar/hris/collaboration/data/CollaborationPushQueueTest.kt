package dev.fajar.hris.collaboration.data

import dev.fajar.hris.collaboration.data.datasources.*
import dev.fajar.hris.collaboration.data.repositories.*
import dev.fajar.hris.collaboration.domain.entities.*
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
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.*
import org.springframework.jdbc.support.JdbcTransactionManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
class CollaborationPushQueueTest {
    companion object {
        @Container
        @JvmStatic
        val postgres =
            PostgreSQLContainer(requireNotNull(System.getProperty("hris.postgres.image")))
        lateinit var owner: JdbcTemplate
        private val keyring =
            parseIdentityKeyring(
                "fixture",
                "fixture:" + Base64.getEncoder().encodeToString(ByteArray(32) { 29 }),
            )

        @BeforeAll
        @JvmStatic
        fun migrate() {
            val source =
                DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            owner = JdbcTemplate(source)
            Flyway.configure().dataSource(source).target("115").load().migrate()
            for (role in listOf("chat_hint_fixture", "chat_hint_worker")) {
                owner.execute("create role $role login password 'chat-hint-fixture-only'")
                owner.execute("grant usage on schema public to $role")
                owner.execute(
                    "grant select,insert,update,delete on all tables in schema public to $role"
                )
            }
            owner.execute("grant hris_worker_capability to chat_hint_worker")
            Fixture()
            val before =
                owner.queryForList(
                    "select row_to_json(b)::text as snapshot from collaboration_push_bindings b order by company_id,device_id"
                )
            assertEquals(1, before.size)
            Flyway.configure().dataSource(source).target("116").load().migrate()
            assertEquals(
                before,
                owner.queryForList(
                    "select row_to_json(b)::text as snapshot from collaboration_push_bindings b order by company_id,device_id"
                ),
            )
            for (role in listOf("chat_hint_fixture", "chat_hint_worker")) owner.execute(
                "grant select,insert,update,delete on all tables in schema public to $role"
            )
        }
    }

    @AfterEach
    fun isolateOwnedFixtureQueue() {
        // Test-owner cleanup only. Runtime roles have no TRUNCATE grant; DELETE remains guarded.
        owner.execute("truncate collaboration_push_hints")
    }

    private fun <T> success(result: Result<T>): T {
        assertInstanceOf(Result.Success::class.java, result)
        return (result as Result.Success).value
    }

    private fun event(index: Int) =
        CollaborationPushEvent("!fixture:matrix.example.test", "$" + "fixture-$index")

    @Test
    fun receiptReplayAndQuietCoalescingPreserveOneBoundedHead() {
        val f = Fixture()
        assertTrue(success(f.append(event(1))))
        val original = f.head()
        assertFalse(success(f.append(event(1))))
        assertFalse(success(f.append(null)))
        assertEquals(original, f.head())
        for (i in 2..70) assertTrue(success(f.append(event(i))))
        val current = f.head()
        assertEquals(70, current.revision)
        assertEquals(64, current.recentEventCount)
        assertEquals(event(70), current.event)
        assertEquals(original.createdAt, current.createdAt)
        assertFalse(success(f.append(event(70))))
        // Older-than-window retries may wake again; Matrix/local original identity dedups display.
        assertTrue(success(f.append(event(1))))
        assertEquals(64, f.head().recentEventCount)
        assertEquals(
            1,
            owner.queryForObject("select count(*) from collaboration_push_hints", Int::class.java),
        )
        val hashes =
            owner.queryForObject(
                "select array_to_string(recent_event_hashes,',') from collaboration_push_hints",
                String::class.java,
            )!!
        assertFalse(hashes.contains("fixture"))
        assertTrue(hashes.split(',').all { it.matches(Regex("[a-f0-9]{64}")) })
    }

    @Test
    fun ownerScopeCannotBorrowAnotherQueueOrClaimWorkerAuthority() {
        val f = Fixture()
        success(f.append(event(1)))
        for (actor in
            listOf(
                f.actor.copy(accountId = UUID.randomUUID()),
                f.actor.copy(companyId = UUID.randomUUID()),
            )) {
            assertNull(
                success(
                    f.runtime.run(actor) {
                        f.runtime.repo.find(f.company, f.account, f.device, true)
                    }
                )
            )
            assertInstanceOf(
                Result.Failed::class.java,
                f.runtime.run(actor) { f.runtime.repo.append(f.binding, event(2), 300) },
            )
        }
        assertInstanceOf(
            Result.Failed::class.java,
            f.runtime.run(f.actor) { f.runtime.repo.claim(UUID.randomUUID(), 1, 30, 8) },
        )
        assertInstanceOf(
            Result.Failed::class.java,
            f.runtime.run(f.actor) {
                f.runtime.repo.append(
                    f.binding.copy(nativeSessionId = UUID.randomUUID()),
                    event(2),
                    300,
                )
            },
        )
        assertEquals(
            0,
            f.runtime.sql.queryForObject(
                "select count(*) from collaboration_push_hints",
                Int::class.java,
            ),
        )
        val worker = Runtime(true)
        val lease = requireNotNull(success(worker.claim()).single().lease)
        assertInstanceOf(
            Result.Failed::class.java,
            f.runtime.run(f.actor) { f.runtime.repo.finish(lease) },
        )
        assertEquals(Result.Success(true), worker.run { worker.repo.finish(lease) })
    }

    @Test
    fun parallelDuplicateCallbacksAdvanceOnceAndDistinctCallbacksDoNotLoseUpdates() {
        val f = Fixture()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val ready = CountDownLatch(4)
            val start = CountDownLatch(1)
            val tasks =
                (1..4).map {
                    pool.submit<Boolean> {
                        ready.countDown()
                        check(start.await(5, TimeUnit.SECONDS))
                        success(f.append(event(1)))
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            assertEquals(1, tasks.count { it.get(10, TimeUnit.SECONDS) })
            val next = (2..5).map { i -> pool.submit<Boolean> { success(f.append(event(i))) } }
            assertTrue(next.all { it.get(10, TimeUnit.SECONDS) })
            assertEquals(5, f.head().revision)
            assertEquals(5, f.head().recentEventCount)
            for (i in 1..5) assertFalse(success(f.append(event(i))))
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun callbacksDuringDeliverySurviveOldAcknowledgementsAndQuietHintsStayQuiet() {
        val f = Fixture()
        val worker = Runtime(true)
        success(f.append(event(1)))
        val first = success(worker.claim()).single()
        val lease = requireNotNull(first.lease)
        assertTrue(success(f.append(event(2))))
        assertEquals(lease, f.head().lease)
        assertEquals(Result.Success(true), worker.run { worker.repo.finish(lease) })
        val second = success(worker.claim()).single()
        assertEquals(2, second.revision)
        assertEquals(1, second.deliveredRevision)
        assertEquals(event(2), second.event)
        assertTrue(success(f.append(null)))
        assertEquals(
            Result.Success(true),
            worker.run { worker.repo.finish(requireNotNull(second.lease)) },
        )
        val quiet = success(worker.claim()).single()
        assertEquals(3, quiet.revision)
        assertEquals(2, quiet.deliveredRevision)
        assertEquals(2, quiet.visibleRevision)
        assertEquals(event(2), quiet.event)
        assertEquals(
            Result.Success(true),
            worker.run { worker.repo.finish(requireNotNull(quiet.lease)) },
        )
        assertTrue(success(worker.claim()).isEmpty())
        assertFalse(success(f.append(event(1))))
        assertFalse(success(f.append(event(2))))
        assertTrue(success(f.append(null)))
        assertEquals(4, f.head().revision)
        assertEquals(2, f.head().visibleRevision)
    }

    @Test
    fun expiredAndForeignLeasesCannotCompleteAReplacementWorkersClaim() {
        val f = Fixture()
        val worker = Runtime(true)
        success(f.append(event(1)))
        val first = requireNotNull(success(worker.claim(seconds = 1)).single().lease)
        for (changed in
            listOf(
                first.copy(ownerId = UUID.randomUUID()),
                first.copy(token = UUID.randomUUID()),
                first.copy(accountId = UUID.randomUUID()),
                first.copy(nativeSessionId = UUID.randomUUID()),
                first.copy(companyId = UUID.randomUUID()),
                first.copy(deviceId = UUID.randomUUID()),
                first.copy(revision = 2),
                first.copy(expiresAt = first.expiresAt.plusSeconds(1)),
            )) assertEquals(Result.Success(false), worker.run { worker.repo.finish(changed) })
        Thread.sleep(
            Duration.between(Instant.now(), first.expiresAt).toMillis().coerceAtLeast(0) + 100
        )
        assertEquals(Result.Success(false), worker.run { worker.repo.finish(first) })
        val next = success(worker.claim()).single()
        assertEquals(2, next.attempts)
        assertNotEquals(first.token, requireNotNull(next.lease).token)
        assertEquals(Result.Success(false), worker.run { worker.repo.defer(first, Instant.now()) })
        assertEquals(
            Result.Success(true),
            worker.run { worker.repo.finish(requireNotNull(next.lease)) },
        )
        assertEquals(
            Result.Success(false),
            worker.run { worker.repo.finish(requireNotNull(next.lease)) },
        )
    }

    @Test
    fun retryBudgetsAreFinitePerRevisionAndNewEventsReleaseStaleBackoff() {
        val f = Fixture()
        val worker = Runtime(true)
        success(f.append(event(1)))
        repeat(8) { index ->
            val head = success(worker.claim()).single()
            assertEquals(index + 1, head.attempts)
            assertEquals(
                Result.Success(true),
                worker.run { worker.repo.defer(requireNotNull(head.lease), Instant.now()) },
            )
        }
        assertTrue(success(worker.claim()).isEmpty())
        assertFalse(success(f.append(event(1))))
        assertTrue(success(worker.claim()).isEmpty())
        assertTrue(success(f.append(event(2))))
        val next = success(worker.claim()).single()
        assertEquals(1, next.attempts)
        assertTrue(success(f.append(event(3))))
        assertEquals(
            Result.Success(true),
            worker.run {
                worker.repo.defer(requireNotNull(next.lease), Instant.now().plusSeconds(100))
            },
        )
        assertEquals(3, success(worker.claim()).single().revision)
    }

    @Test
    fun claimFanoutAndGlobalCapacityStayBoundedAcrossWorkers() {
        repeat(24) { success(Fixture().append(event(it))) }
        val worker = Runtime(true)
        val pool = Executors.newFixedThreadPool(3)
        val claimed = mutableListOf<CollaborationPushHead>()
        try {
            val ready = CountDownLatch(3)
            val start = CountDownLatch(1)
            val tasks =
                (1..3).map {
                    pool.submit<List<CollaborationPushHead>> {
                        ready.countDown()
                        check(start.await(5, TimeUnit.SECONDS))
                        success(Runtime(true).claim(limit = 8))
                    }
                }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            tasks.forEach { claimed += it.get(10, TimeUnit.SECONDS) }
            assertTrue(claimed.size in 8..16)
            assertEquals(claimed.size, claimed.map { it.deviceId }.toSet().size)
        } finally {
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
        // A try-lock contender can yield an empty batch. Complete the bounded capacity
        // sequentially, without interpreting contention as a permanently empty queue.
        repeat(2) { if (claimed.size < 16) claimed += success(worker.claim(limit = 8)) }
        assertEquals(16, claimed.size)
        assertEquals(16, claimed.map { it.deviceId }.toSet().size)
        assertTrue(success(Runtime(true).claim(limit = 8)).isEmpty())
        for (head in claimed) assertEquals(
            Result.Success(true),
            worker.run { worker.repo.finish(requireNotNull(head.lease)) },
        )
        assertEquals(8, success(worker.claim(limit = 8)).size)
        assertInstanceOf(
            Result.Failed::class.java,
            worker.run { worker.repo.claim(UUID.randomUUID(), 9, 30, 8) },
        )
        assertThrows(DataAccessException::class.java) {
            owner.queryForObject(
                "select count(*) from claim_collaboration_push_hints(gen_random_uuid(),null,30,8)",
                Int::class.java,
            )
        }
    }

    @Test
    fun requestFailureAndInterruptionRollBackHintsAndAcknowledgements() {
        val f = Fixture()
        val worker = Runtime(true)
        val denial = Result.Failed(Failure(FailureKind.FORBIDDEN, "fixture_denied"))
        assertEquals(
            denial,
            f.runtime.run(f.actor) {
                assertTrue(success(f.runtime.repo.append(f.binding, event(1), 300)))
                denial
            },
        )
        assertNull(
            success(f.runtime.run(f.actor) { f.runtime.repo.find(f.company, f.account, f.device) })
        )
        assertThrows(InterruptedException::class.java) {
            f.runtime.run<Unit>(f.actor) {
                success(f.runtime.repo.append(f.binding, event(1), 300))
                throw InterruptedException("fixture")
            }
        }
        success(f.append(event(1)))
        val lease = requireNotNull(success(worker.claim()).single().lease)
        assertEquals(
            denial,
            worker.run {
                assertTrue(success(worker.repo.finish(lease)))
                denial
            },
        )
        assertEquals(lease, f.head().lease)
        assertEquals(0, f.head().deliveredRevision)
        assertEquals(Result.Success(true), worker.run { worker.repo.finish(lease) })
    }

    @Test
    fun shortHintExpiryIsRealAndStructuralMutationsCannotRewriteProgress() {
        val f = Fixture()
        val worker = Runtime(true)
        success(f.append(event(1), lifetime = 1))
        val head = f.head()
        Thread.sleep(
            Duration.between(Instant.now(), head.expiresAt).toMillis().coerceAtLeast(0) + 100
        )
        assertTrue(success(worker.claim()).isEmpty())
        for (assignment in
            listOf(
                "revision=revision+2",
                "delivered_revision=revision",
                "event_id='changed'",
                "native_session_id=gen_random_uuid()",
                "created_at=created_at-interval '1 second'",
            )) assertThrows(DataAccessException::class.java) {
            owner.update(
                "update collaboration_push_hints set $assignment where company_id=?",
                f.company,
            )
        }
        assertThrows(DataAccessException::class.java) {
            owner.update("delete from collaboration_push_hints where company_id=?", f.company)
        }
        assertTrue(success(f.append(event(2))))
        val lease = requireNotNull(success(worker.claim()).single().lease)
        assertInstanceOf(
            Result.Failed::class.java,
            worker.run { worker.repo.defer(lease, Instant.now().plusSeconds(301)) },
        )
        assertEquals(lease, f.head().lease)
    }

    @Test
    fun restoredQueueKeepsInFlightIdentityAndNewerCallbacksBesideEncryptedBindings() {
        val f = Fixture()
        val worker = Runtime(true)
        val key = success(f.runtime.run(f.actor) { f.bindings.key(f.binding) })!!.value
        success(f.append(event(1)))
        val lease = requireNotNull(success(worker.claim(seconds = 60)).single().lease)
        success(f.append(event(2)))
        val before = f.head()
        fun command(vararg args: String) {
            val result = postgres.execInContainer("timeout", "20", *args)
            assertEquals(0, result.exitCode, "Owned fixture dump/restore command failed")
        }
        command(
            "pg_dump",
            "-U",
            postgres.username,
            "-d",
            postgres.databaseName,
            "-Fc",
            "-f",
            "/tmp/chat-hints.dump",
        )
        command("createdb", "-U", postgres.username, "chat_hints_restored")
        command(
            "pg_restore",
            "-U",
            postgres.username,
            "-d",
            "chat_hints_restored",
            "--exit-on-error",
            "/tmp/chat-hints.dump",
        )
        val url =
            postgres.jdbcUrl.substringBefore('?').substringBeforeLast('/') + "/chat_hints_restored"
        val restored = Runtime(true, url)
        val normal = Runtime(false, url)
        assertEquals(
            before,
            success(normal.run(f.actor) { normal.repo.find(f.company, f.account, f.device) }),
        )
        val bindings =
            EncryptedCollaborationPushBindingRepository(
                PostgresCollaborationPushBindingDataSource(normal.jooq),
                JceIdentityTokenDataSource(keyring),
            )
        assertTrue(key == success(normal.run(f.actor) { bindings.key(f.binding) })!!.value)
        assertNull(
            success(
                normal.run(f.actor.copy(companyId = UUID.randomUUID())) {
                    normal.repo.find(f.company, f.account, f.device)
                }
            )
        )
        assertEquals(Result.Success(true), restored.run { restored.repo.finish(lease) })
        val pending = success(restored.claim()).single()
        assertEquals(2, pending.revision)
        assertEquals(1, pending.deliveredRevision)
        assertEquals(event(2), pending.event)
        assertEquals(
            Result.Success(true),
            restored.run { restored.repo.finish(requireNotNull(pending.lease)) },
        )
        assertEquals(before, f.head())
        assertEquals(
            Result.Success(false),
            normal.run(f.actor) { normal.repo.append(f.binding, event(2), 300) },
        )
    }

    @Test
    fun newQuietHintCanRecoverAfterASmallerWorkersRetryBudget() {
        val f = Fixture()
        val worker = Runtime(true)
        success(f.append(null))
        repeat(3) {
            val head =
                success(worker.run { worker.repo.claim(UUID.randomUUID(), 1, 30, 3) }).single()
            assertEquals(
                Result.Success(true),
                worker.run { worker.repo.defer(requireNotNull(head.lease), Instant.now()) },
            )
        }
        assertTrue(success(worker.run { worker.repo.claim(UUID.randomUUID(), 1, 30, 3) }).isEmpty())
        assertTrue(success(f.append(null)))
        val next = success(worker.run { worker.repo.claim(UUID.randomUUID(), 1, 30, 3) }).single()
        assertEquals(2, next.revision)
        assertEquals(1, next.attempts)
        assertEquals(0, next.visibleRevision)
    }

    private class Runtime(worker: Boolean = false, url: String = postgres.jdbcUrl) {
        private val source =
            DriverManagerDataSource(
                url,
                if (worker) "chat_hint_worker" else "chat_hint_fixture",
                "chat-hint-fixture-only",
            )
        val sql = JdbcTemplate(source)
        private val tx = PostgresTransactionRunner(JdbcTransactionManager(source), sql)
        val jooq = DSL.using(TransactionAwareDataSourceProxy(source), SQLDialect.POSTGRES)
        val repo =
            StoredCollaborationPushQueueRepository(PostgresCollaborationPushQueueDataSource(jooq))
        val actor = Actor(UUID(0, 0), null, emptySet(), Instant.now(), UUID.randomUUID())

        fun <T> run(actor: Actor = this.actor, work: () -> Result<T>): Result<T> =
            tx.run(actor, work)

        fun claim(limit: Int = 1, seconds: Int = 30): Result<List<CollaborationPushHead>> = run {
            repo.claim(UUID.randomUUID(), limit, seconds, 8)
        }
    }

    private class Fixture {
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        val parent = UUID.randomUUID()
        val device = UUID.randomUUID()
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val actor = Actor(account, company, emptySet(), now, UUID.randomUUID())
        val runtime = Runtime()
        val binding = CollaborationPushBinding(company, account, device, parent, 0, now, null)
        val bindings =
            EncryptedCollaborationPushBindingRepository(
                PostgresCollaborationPushBindingDataSource(runtime.jooq),
                JceIdentityTokenDataSource(keyring),
            )

        init {
            owner.update(
                "insert into companies(id,code,name,timezone) values(?,?,'Fictional hint company','UTC')",
                company,
                company.toString().replace("-", ""),
            )
            owner.update(
                "insert into accounts(id,email,display_name) values(?,?,'Fictional hint participant')",
                account,
                "$account@example.test",
            )
            val before = now.minusSeconds(120).atOffset(ZoneOffset.UTC)
            val hash = parent.toString().replace("-", "").repeat(2)
            owner.update(
                "insert into native_sessions(id,account_id,refresh_hash,access_hash,authenticated_at,expires_at,access_expires_at,created_at,rotated_at) values(?,?,?,?,?,?,?,?,?)",
                parent,
                account,
                hash,
                hash,
                before,
                now.plusSeconds(86400).atOffset(ZoneOffset.UTC),
                now.plusSeconds(900).atOffset(ZoneOffset.UTC),
                before,
                before,
            )
            owner.update(
                "insert into collaboration_devices(company_id,id,account_id,platform,label,state,version,created_at,updated_at) values(?,?,?,'ANDROID','Fictional hint device','PROVISIONING',0,?,?)",
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
            check(runtime.run(actor) { bindings.reserve(binding) } is Result.Success)
        }

        fun append(event: CollaborationPushEvent?, lifetime: Int = 300) =
            runtime.run(actor) { runtime.repo.append(binding, event, lifetime) }

        fun head(): CollaborationPushHead =
            (runtime.run(actor) { runtime.repo.find(company, account, device) } as Result.Success)
                .value!!
    }
}
