package dev.fajar.hris.worker

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.data.datasources.IdentityTokenDataSource
import dev.fajar.hris.identity.data.datasources.NativeSessionCleanupDataSource
import dev.fajar.hris.identity.data.models.NativeSessionCleanupData
import dev.fajar.hris.identity.data.repositories.StoredNativeSessionCleanupRepository
import dev.fajar.hris.identity.domain.entities.NativeSessionCleanupResult
import dev.fajar.hris.identity.domain.usecases.MaintainNativeSessions
import dev.fajar.hris.identity.domain.usecases.RefreshNativeSession
import dev.fajar.hris.identity.domain.usecases.ResolveNativeAccess
import dev.fajar.hris.worker.identity.NativeSessionCleanupWorker
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["hris.worker.enabled=false"],
)
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS
)
class NativeSessionCleanupWorkerTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18.6-alpine").withInitScript("worker-role.sql")

        @DynamicPropertySource
        @JvmStatic
        fun configuration(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { "hris_worker_test" }
            registry.add("spring.datasource.password") { "worker-fixture-only" }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.url") { postgres.jdbcUrl }
            registry.add("spring.flyway.user") { postgres.username }
            registry.add("spring.flyway.password") { postgres.password }
        }
    }

    @Autowired private lateinit var source: NativeSessionCleanupDataSource
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var tokens: IdentityTokenDataSource
    @Autowired private lateinit var resolve: ResolveNativeAccess
    @Autowired private lateinit var refresh: RefreshNativeSession
    @Autowired private lateinit var context: ApplicationContext
    private lateinit var now: Instant
    private val clock
        get() = Clock.fixed(now, ZoneOffset.UTC)

    private fun database() =
        JdbcTemplate(
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        )

    private fun maintain(adapter: NativeSessionCleanupDataSource = source) =
        MaintainNativeSessions(StoredNativeSessionCleanupRepository(adapter), transactions, clock)

    @BeforeEach
    fun clearDisposableSessions() {
        // This container belongs only to this class; no development account is accessed.
        database().update("delete from native_sessions")
        now = Instant.now().minusSeconds(2).truncatedTo(ChronoUnit.MICROS)
    }

    private data class Fixture(
        val id: UUID,
        val account: UUID,
        val accessToken: String,
        val expiry: Instant,
    )

    private fun fixture(
        expiry: Instant = now.plusSeconds(86400),
        replay: Instant? = now.minusSeconds(1),
        revoked: Boolean = false,
    ): Fixture {
        val id = UUID.randomUUID()
        val account = UUID.randomUUID()
        val access = tokens.generate()
        database()
            .update(
                "insert into accounts(id,email,display_name) values(?,?,'Owned session cleanup fixture')",
                account,
                "$account@example.invalid",
            )
        database()
            .update(
                """insert into native_sessions(id,account_id,refresh_hash,access_hash,authenticated_at,expires_at,access_expires_at,
               revoked_at,version,credential_version,device_name,created_at,exchange_encrypted,exchange_replay_until,rotated_at)
               values(?,?,?,?,?,?,?, ?,1,0,'Owned cleanup device',?,'owned-exchange-envelope',?,?)""",
                id,
                account,
                tokens.hash(tokens.generate()),
                tokens.hash(access),
                now.minusSeconds(60).atOffset(ZoneOffset.UTC),
                expiry.atOffset(ZoneOffset.UTC),
                minOf(expiry, now.plusSeconds(600)).atOffset(ZoneOffset.UTC),
                if (revoked) now.minusSeconds(30).atOffset(ZoneOffset.UTC) else null,
                now.minusSeconds(40 * 86400).atOffset(ZoneOffset.UTC),
                replay?.atOffset(ZoneOffset.UTC),
                now.minusSeconds(60).atOffset(ZoneOffset.UTC),
            )
        return Fixture(id, account, access, expiry)
    }

    private fun consumed(session: Fixture, replay: Instant? = now.minusSeconds(1)): String {
        val token = tokens.generate()
        database()
            .update(
                """insert into consumed_refresh_tokens(token_hash,session_id,expires_at,successor_version,replay_encrypted,replay_until)
            values(?,?,?,1,'owned-refresh-envelope',?)""",
                tokens.hash(token),
                session.id,
                session.expiry.atOffset(ZoneOffset.UTC),
                replay?.atOffset(ZoneOffset.UTC),
            )
        return token
    }

    private fun count(table: String): Int =
        requireNotNull(database().queryForObject("select count(*) from $table", Int::class.java))

    private fun exists(id: UUID): Boolean =
        database()
            .queryForObject(
                "select exists(select 1 from native_sessions where id=?)",
                Boolean::class.java,
                id,
            ) == true

    private fun exchange(id: UUID): String? =
        database()
            .queryForObject(
                "select exchange_encrypted from native_sessions where id=?",
                String::class.java,
                id,
            )

    private fun replay(token: String): String? =
        database()
            .queryForObject(
                "select replay_encrypted from consumed_refresh_tokens where token_hash=?",
                String::class.java,
                tokens.hash(token),
            )

    private fun report(result: Result<NativeSessionCleanupResult>): NativeSessionCleanupResult {
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    @Test
    fun retentionPreservesActiveCredentialsQuotaEvidenceAndRefreshReuseDetection() {
        val expired = fixture(now.minusSeconds(86400))
        consumed(expired)
        database()
            .update(
                """insert into native_push_registrations(session_id,account_id,platform,token_hash,token_encrypted,enabled,version,registered_at,updated_at,expires_at)
            values(?,?,'ANDROID',?,'owned-push-envelope',true,0,?,?,?)""",
                expired.id,
                expired.account,
                tokens.hash(tokens.generate()),
                expired.expiry.minusSeconds(600).atOffset(ZoneOffset.UTC),
                expired.expiry.minusSeconds(600).atOffset(ZoneOffset.UTC),
                expired.expiry.atOffset(ZoneOffset.UTC),
            )
        val grace = fixture(now.minusSeconds(86399))
        val active = fixture(replay = now.plusSeconds(300))
        val unexpiredReplay = consumed(active, now.plusSeconds(300))
        val consumedToken = consumed(active)
        val revoked = fixture(revoked = true)
        val inactive = fixture()
        database().update("update accounts set active=false where id=?", inactive.account)
        val result = report(maintain().execute())
        assertEquals(NativeSessionCleanupResult(1, 3, 1), result)
        assertFalse(exists(expired.id))
        assertEquals(0, count("native_push_registrations"))
        assertTrue(exists(grace.id) && exists(revoked.id) && exists(inactive.id))
        assertEquals("owned-exchange-envelope", exchange(active.id))
        assertEquals("owned-refresh-envelope", replay(unexpiredReplay))
        assertNull(replay(consumedToken))
        assertEquals(2, count("consumed_refresh_tokens"))
        assertEquals(
            1L,
            database()
                .queryForObject(
                    "select version from native_sessions where id=?",
                    Long::class.java,
                    active.id,
                ),
        )
        assertTrue(resolve.execute(active.accessToken) is Result.Success)
        assertEquals(
            "native_session_revoked",
            (refresh.execute(consumedToken, UUID.randomUUID(), UUID.randomUUID()) as Result.Failed)
                .failure
                .code,
        )
        assertTrue(resolve.execute(active.accessToken) is Result.Failed)
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='identity.refresh_reuse_detected'",
                    Int::class.java,
                    active.id,
                ),
        )
    }

    @Test
    fun eachIndependentQueueHasAFiniteLimitAndEventuallyDrains() {
        repeat(3) { fixture(now.minusSeconds(86401)) }
        val active = List(5) { fixture() }
        repeat(5) { consumed(active.first()) }
        assertEquals(NativeSessionCleanupResult(1, 2, 2), report(maintain().execute(1, 2)))
        assertEquals(7, count("native_sessions"))
        assertEquals(
            5,
            database()
                .queryForObject(
                    "select count(*) from native_sessions where exchange_encrypted is not null",
                    Int::class.java,
                ),
        )
        assertEquals(5, count("consumed_refresh_tokens"))
        var drained = false
        repeat(4) {
            val result = report(maintain().execute(1, 2))
            assertTrue(
                result.deletedSessions in 0..1 &&
                    result.clearedExchangeReplays in 0..2 &&
                    result.clearedRefreshReplays in 0..2
            )
            if (result == NativeSessionCleanupResult(0, 0, 0)) drained = true
        }
        assertTrue(drained)
        assertEquals(5, count("native_sessions"))
        assertEquals(5, count("consumed_refresh_tokens"))
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from consumed_refresh_tokens where replay_encrypted is not null",
                    Int::class.java,
                ),
        )
    }

    @Test
    fun maximumRotationEvidenceCascadesOnlyWithItsExpiredParent() {
        val expired = fixture(now.minusSeconds(86401))
        database()
            .update(
                """insert into consumed_refresh_tokens(token_hash,session_id,expires_at,successor_version,replay_encrypted,replay_until)
            select md5(?::text||n::text)||md5(n::text||?::text),?,?,n,'owned-expired-envelope',? from generate_series(1,10000) n""",
                expired.id,
                expired.id,
                expired.id,
                expired.expiry.atOffset(ZoneOffset.UTC),
                now.minusSeconds(1).atOffset(ZoneOffset.UTC),
            )
        assertEquals(10000, count("consumed_refresh_tokens"))
        assertEquals(NativeSessionCleanupResult(1, 0, 0), report(maintain().execute(1, 1)))
        assertEquals(0, count("consumed_refresh_tokens"))
    }

    @Test
    fun competingWorkersSkipOwnedRowsInsteadOfDuplicatingWork() {
        repeat(2) { fixture(now.minusSeconds(86401)) }
        val active = List(2) { fixture() }
        active.forEach { consumed(it) }
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val probe = NativeSessionCleanupDataSource { expired, replay, sessions, replays ->
            val result = source.prune(expired, replay, sessions, replays)
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            result
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val tasks =
                List(2) {
                    executor.submit<NativeSessionCleanupResult> {
                        report(maintain(probe).execute(1, 1))
                    }
                }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            release.countDown()
            assertEquals(
                listOf(NativeSessionCleanupResult(1, 1, 1), NativeSessionCleanupResult(1, 1, 1)),
                tasks.map { it.get(5, TimeUnit.SECONDS) },
            )
            assertEquals(2, count("native_sessions"))
            assertEquals(NativeSessionCleanupResult(0, 0, 0), report(maintain().execute()))
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun aHeldCredentialOrConsumedHashDoesNotBlockOtherEligibleWork() {
        val heldSession = fixture(now.minusSeconds(86401))
        val available = fixture(now.minusSeconds(86400))
        val active = fixture(replay = now.plusSeconds(300))
        val heldToken = consumed(active)
        val availableToken = consumed(active)
        DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .connection
            .use { connection ->
                connection.autoCommit = false
                connection
                    .prepareStatement("select id from native_sessions where id=? for update")
                    .use {
                        it.setObject(1, heldSession.id)
                        it.executeQuery().close()
                    }
                connection
                    .prepareStatement(
                        "select token_hash from consumed_refresh_tokens where token_hash=? for update"
                    )
                    .use {
                        it.setString(1, tokens.hash(heldToken))
                        it.executeQuery().close()
                    }
                assertTimeoutPreemptively(Duration.ofSeconds(3)) {
                    assertEquals(
                        NativeSessionCleanupResult(1, 0, 1),
                        report(maintain().execute(1, 1)),
                    )
                }
                assertTrue(exists(heldSession.id))
                assertFalse(exists(available.id))
                assertEquals("owned-refresh-envelope", replay(heldToken))
                assertNull(replay(availableToken))
                connection.rollback()
            }
        assertEquals(NativeSessionCleanupResult(1, 0, 1), report(maintain().execute(1, 1)))
    }

    @Test
    fun invalidOrFutureCutoffsAndMissingWorkerCapabilityCannotDeleteRows() {
        val entry = fixture(now.minusSeconds(86401))
        database()
            .execute(
                "create role cleanup_api_test login password 'owned-api-fixture' nosuperuser nocreatedb nocreaterole nobypassrls"
            )
        database().execute("grant usage on schema public to cleanup_api_test")
        database()
            .execute(
                "grant select,update,delete on native_sessions,consumed_refresh_tokens to cleanup_api_test"
            )
        val api =
            JdbcTemplate(
                DriverManagerDataSource(postgres.jdbcUrl, "cleanup_api_test", "owned-api-fixture")
            )
        assertThrows(org.springframework.dao.DataAccessException::class.java) {
            api.queryForMap(
                "select * from prune_native_session_material(?::timestamptz,?::timestamptz,1,1)",
                now.minusSeconds(86400).atOffset(ZoneOffset.UTC),
                now.atOffset(ZoneOffset.UTC),
            )
        }
        assertFalse(
            requireNotNull(
                database()
                    .queryForObject(
                        "select has_function_privilege('cleanup_api_test','prune_native_session_material(timestamptz,timestamptz,integer,integer)','EXECUTE')",
                        Boolean::class.java,
                    )
            )
        )
        for (bounds in
            listOf(
                "null,now(),1,1",
                "now(),now(),1,1",
                "now()-interval '48 hours',now()+interval '1 hour',1,1",
                "'-infinity'::timestamptz,now(),1,1",
                "now()-interval '48 hours',now(),17,1",
                "now()-interval '48 hours',now(),1,513",
            )) {
            assertThrows(org.springframework.dao.DataAccessException::class.java) {
                database().queryForMap("select * from prune_native_session_material($bounds)")
            }
            assertTrue(exists(entry.id))
        }
        assertEquals(1, report(maintain().execute()).deletedSessions)
    }

    @Test
    fun aLateMappingFailureRollsBackAllThreeKindsOfCleanup() {
        val expired = fixture(now.minusSeconds(86401))
        val active = fixture()
        val token = consumed(active)
        val invalid = NativeSessionCleanupDataSource { before, replay, sessions, replays ->
            source.prune(before, replay, sessions, replays)
            NativeSessionCleanupData(sessions + 1, 0, 0)
        }
        assertEquals(
            "database_failure",
            (maintain(invalid).execute() as Result.Failed).failure.code,
        )
        assertTrue(exists(expired.id))
        assertEquals("owned-exchange-envelope", exchange(active.id))
        assertEquals("owned-refresh-envelope", replay(token))
        assertEquals(NativeSessionCleanupResult(1, 1, 1), report(maintain().execute()))
    }

    private fun timer(threads: MutableList<Thread>) =
        ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadFactory { task ->
                Thread(task, "native-cleanup-fixture-${UUID.randomUUID()}").also { threads += it }
            }
            setRemoveOnCancelPolicy(true)
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
            setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
            setWaitForTasksToCompleteOnShutdown(false)
            setAwaitTerminationSeconds(3)
            initialize()
        }

    @Test
    fun stoppingAPendingPassRollsBackAndReleasesTheThreadQueueAndRowGuards() {
        val expired = fixture(now.minusSeconds(86401))
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val calls = AtomicInteger()
        val probe = NativeSessionCleanupDataSource { before, replay, sessions, replays ->
            calls.incrementAndGet()
            val result = source.prune(before, replay, sessions, replays)
            entered.countDown()
            try {
                check(release.await(5, TimeUnit.SECONDS))
                result
            } finally {
                exited.countDown()
            }
        }
        val threads = CopyOnWriteArrayList<Thread>()
        val timer = timer(threads)
        val worker = NativeSessionCleanupWorker(maintain(probe), timer, Duration.ofSeconds(1))
        val callback = CountDownLatch(1)
        try {
            worker.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            worker.stop(Runnable { callback.countDown() })
            assertTrue(callback.await(3, TimeUnit.SECONDS))
            assertTrue(exited.await(3, TimeUnit.SECONDS))
            assertFalse(worker.isRunning)
            assertTrue(timer.scheduledThreadPoolExecutor.awaitTermination(3, TimeUnit.SECONDS))
            threads.forEach { it.join(1000) }
            assertTrue(threads.none { it.isAlive })
            assertTrue(timer.scheduledThreadPoolExecutor.queue.isEmpty())
            assertEquals(1, calls.get())
            assertTrue(exists(expired.id))
            assertEquals(1, report(maintain().execute()).deletedSessions)
            assertThrows(IllegalStateException::class.java) { worker.start() }
        } finally {
            release.countDown()
            worker.stop()
        }
    }

    @Test
    fun fixedDelayKeepsOneTaskAndAppliesTheNextBatchOnlyAfterCommit() {
        repeat(5) { fixture(now.minusSeconds(86401)) }
        val second = CountDownLatch(1)
        val release = CountDownLatch(1)
        val committed = CountDownLatch(1)
        val calls = AtomicInteger()
        val probe = NativeSessionCleanupDataSource { before, replay, sessions, replays ->
            assertEquals(4, sessions)
            assertEquals(128, replays)
            val result = source.prune(before, replay, sessions, replays)
            if (calls.incrementAndGet() == 2) {
                second.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                TransactionSynchronizationManager.registerSynchronization(
                    object : TransactionSynchronization {
                        override fun afterCommit() {
                            committed.countDown()
                        }
                    }
                )
            }
            result
        }
        val threads = CopyOnWriteArrayList<Thread>()
        val timer = timer(threads)
        val worker = NativeSessionCleanupWorker(maintain(probe), timer, Duration.ofSeconds(1))
        try {
            worker.start()
            assertTrue(second.await(5, TimeUnit.SECONDS))
            assertEquals(1, count("native_sessions"))
            assertEquals(1, threads.size)
            assertTrue(timer.scheduledThreadPoolExecutor.queue.size <= 1)
            release.countDown()
            assertTrue(committed.await(5, TimeUnit.SECONDS))
            worker.stop()
            assertTrue(timer.scheduledThreadPoolExecutor.awaitTermination(3, TimeUnit.SECONDS))
            assertTrue(timer.scheduledThreadPoolExecutor.queue.isEmpty())
            assertEquals(0, count("native_sessions"))
        } finally {
            release.countDown()
            worker.stop()
        }
    }

    @Test
    fun disabledWorkerConfigurationAllocatesNoCleanupTaskAndSchedulingRejectionStopsTheOwner() {
        assertFalse(context.containsBean("nativeSessionCleanupWorker"))
        assertFalse(context.containsBean("nativeSessionCleanupTimer"))
        val threads = CopyOnWriteArrayList<Thread>()
        val timer = timer(threads)
        timer.shutdown()
        val worker = NativeSessionCleanupWorker(maintain(), timer)
        try {
            assertThrows(RuntimeException::class.java) { worker.start() }
            assertFalse(worker.isRunning)
            assertTrue(threads.isEmpty())
        } finally {
            worker.stop()
        }
    }
}
