package dev.fajar.hris.worker

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.repositories.*
import dev.fajar.hris.reporting.domain.usecases.MaintainReportExports
import dev.fajar.hris.storage.domain.entities.ObjectCleanupRequest
import dev.fajar.hris.storage.domain.repositories.ObjectCleanupRepository
import dev.fajar.hris.worker.reporting.ReportExportCleanupWorker
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
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
class ReportExportRetentionWorkerTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18.6-alpine").withInitScript("worker-role.sql")

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { "hris_worker_test" }
            registry.add("spring.datasource.password") { "worker-fixture-only" }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.url") { postgres.jdbcUrl }
            registry.add("spring.flyway.user") { postgres.username }
            registry.add("spring.flyway.password") { postgres.password }
        }
    }

    @Autowired private lateinit var expiry: ReportExportExpiryRepository
    @Autowired private lateinit var exports: ReportExportRepository
    @Autowired private lateinit var snapshots: ReportSnapshotRepository
    @Autowired private lateinit var jobs: JobRepository
    @Autowired private lateinit var cleanup: ObjectCleanupRepository
    @Autowired private lateinit var journal: ChangeJournalRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var context: ApplicationContext
    private val clock = Clock.systemUTC()

    private fun database() =
        JdbcTemplate(
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        )

    private fun <T> value(result: Result<T>): T {
        assertTrue(result is Result.Success, "$result")
        return (result as Result.Success).value
    }

    private fun maintain(
        repository: ReportExportExpiryRepository = expiry,
        events: ChangeJournalRepository = journal,
        time: Clock = clock,
    ) = MaintainReportExports(repository, exports, snapshots, jobs, events, transactions, time)

    private fun worker() = Actor(UUID(0, 0), null, emptySet(), clock.instant(), UUID.randomUUID())

    private data class Fixture(val actor: Actor, val key: ReportExportExpiryKey, val job: UUID)

    @BeforeEach
    fun clearDisposableExports() {
        database()
            .execute(
                "truncate report_export_expiry_queue,report_export_parts,report_export_uploads,report_exports,report_snapshot_rows,report_snapshots,object_cleanup_queue cascade"
            )
    }

    private fun fixture(rows: Int = 0, live: Boolean = false): Fixture {
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        val id = UUID.randomUUID()
        val job = UUID.randomUUID()
        val expires =
            Instant.now().truncatedTo(ChronoUnit.MICROS).plusSeconds(if (live) 3600 else -7200)
        val created = expires.minusSeconds(60)
        database()
            .update(
                "insert into companies(id,code,name,timezone) values(?,?,'Retention fixture','UTC')",
                company,
                "R${company.toString().take(8)}",
            )
        database()
            .update(
                "insert into accounts(id,email,display_name,active) values(?,?,'Revoked retention owner',false)",
                account,
                "$account@example.test",
            )
        database()
            .update(
                """insert into background_jobs(id,company_id,actor_id,kind,operation_id,request,authenticated_at,credential_version,correlation_id,total_items,progress_mode)
            values(?,?,?,'REPORT_EXPORT',?,?::jsonb,?::timestamptz,0,?,1026,'UPPER_BOUND')""",
                job,
                company,
                account,
                UUID.randomUUID(),
                "{\"exportId\":\"$id\"}",
                created.toString(),
                UUID.randomUUID(),
            )
        // A future format is intentionally unreadable by current content repositories.
        database()
            .update(
                """insert into report_exports(company_id,account_id,id,definition,columns,sources,client,created_at,expires_at,updated_at,job_id)
            values(?,?,?,'{"schemaVersion":"report-export.v999"}','[{"key":"future","type":"TEXT"}]','{}','{}',?::timestamptz,?::timestamptz,?::timestamptz,?)""",
                company,
                account,
                id,
                created.toString(),
                expires.toString(),
                created.toString(),
                job,
            )
        // Use one owned admin connection for deferred snapshot sealing in fixture setup.
        database().dataSource!!.connection.use { connection ->
            connection.autoCommit = false
            val sql =
                JdbcTemplate(
                    org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true)
                )
            try {
                sql.update(
                    """insert into report_snapshots(company_id,account_id,id,definition,columns,evaluated_at,expires_at)
                    values(?,?,?,'{"schemaVersion":"report-export.v999"}','[{"key":"future","type":"TEXT"}]',?::timestamptz,?::timestamptz)""",
                    company,
                    account,
                    id,
                    created.toString(),
                    expires.toString(),
                )
                sql.update(
                    """insert into report_snapshot_rows(company_id,account_id,snapshot_id,row_number,cells)
                    select ?,?,?,n,jsonb_build_array('retained-fixture-'||n) from generate_series(1,?) n""",
                    company,
                    account,
                    id,
                    rows,
                )
                sql.update(
                    "update report_snapshots set row_count=staged_rows,stored_bytes=staged_bytes where company_id=? and account_id=? and id=?",
                    company,
                    account,
                    id,
                )
                connection.commit()
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            }
        }
        return Fixture(
            Actor(account, company, emptySet(), created, UUID.randomUUID()),
            ReportExportExpiryKey(company, account, id, expires, expires),
            job,
        )
    }

    private fun countRows(f: Fixture) =
        database()
            .queryForObject(
                "select count(*) from report_snapshot_rows where snapshot_id=?",
                Int::class.java,
                f.key.id,
            )!!

    private fun retired(f: Fixture) =
        database()
            .queryForObject(
                "select retired_at is not null from report_exports where id=?",
                Boolean::class.java,
                f.key.id,
            )!!

    private fun wake(f: Fixture) {
        database()
            .update(
                "update report_export_expiry_queue set due_at=expires_at where export_id=?",
                f.key.id,
            )
    }

    @Test
    fun boundedCleanupRetiresUnknownFormatsAndReleasesQuotaWithoutRestoringRevokedAccess() {
        val expired = fixture(1201)
        val live = fixture(7, true)
        assertTrue(
            transactions.run(expired.actor) {
                exports.find(expired.key.companyId, expired.key.accountId, expired.key.id)
            } is Result.Failed
        )
        assertEquals(ReportExportCleanupResult(1, 500, 0), value(maintain().execute()))
        assertEquals(701, countRows(expired))
        assertFalse(retired(expired))
        assertEquals(
            "STOPPED",
            database()
                .queryForObject(
                    "select status from report_exports where id=?",
                    String::class.java,
                    expired.key.id,
                ),
        )
        assertEquals(
            true,
            database()
                .queryForObject(
                    "select cancellation_requested from background_jobs where id=?",
                    Boolean::class.java,
                    expired.job,
                ),
        )
        assertEquals(
            1,
            value(
                    transactions.run(expired.actor) {
                        exports.quota(expired.key.companyId, expired.key.accountId)
                    }
                )
                .retained,
        )
        wake(expired)
        assertEquals(500, value(maintain().execute()).deletedRows)
        wake(expired)
        assertEquals(ReportExportCleanupResult(1, 201, 1), value(maintain().execute()))
        assertTrue(retired(expired))
        assertEquals(0, countRows(expired))
        assertEquals(7, countRows(live))
        assertEquals(
            0,
            value(
                    transactions.run(expired.actor) {
                        exports.quota(expired.key.companyId, expired.key.accountId)
                    }
                )
                .retained,
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from report_snapshots where id=?",
                    Int::class.java,
                    expired.key.id,
                ),
        )
        assertEquals(
            ReportExportCleanupResult(0, 0, 0),
            value(maintain(time = Clock.offset(clock, Duration.ofDays(1))).execute()),
        )
        assertFalse(retired(live))
    }

    @Test
    fun outstandingObjectCleanupBlocksRetirementWithoutStarvingAnotherDueExport() {
        val blocked = fixture()
        val id = UUID.randomUUID()
        value(
            transactions.run(blocked.actor) {
                cleanup.schedule(
                    ObjectCleanupRequest(
                        id,
                        blocked.key.companyId,
                        blocked.key.id,
                        "${blocked.key.companyId}/reports/${blocked.key.id}/$id",
                        42,
                        blocked.key.accountId,
                        clock.instant().minusSeconds(1),
                    )
                )
            }
        )
        assertEquals(ReportExportCleanupResult(1, 0, 0), value(maintain().execute(1)))
        val later = fixture(3)
        assertEquals(ReportExportCleanupResult(1, 3, 1), value(maintain().execute(1)))
        assertTrue(retired(later))
        assertFalse(retired(blocked))
        val lease =
            value(transactions.run(worker()) { cleanup.claim(UUID.randomUUID(), 1, 60, 8) })
                .single()
        assertEquals(id, lease.entry.request.id)
        value(transactions.run(worker()) { cleanup.complete(lease) })
        wake(blocked)
        assertEquals(ReportExportCleanupResult(1, 0, 1), value(maintain().execute(1)))
    }

    @Test
    fun progressingLargeExportsMoveBehindOlderDueWorkAndCompetingPassesDoNotDoubleRetire() {
        val fs = (1..5).map { fixture(501) }
        val first = value(maintain().execute(4))
        assertEquals(2000, first.deletedRows)
        val untouched = fs.single { countRows(it) == 501 }
        assertEquals(500, value(maintain().execute(1)).deletedRows)
        assertEquals(1, countRows(untouched))
        fs.forEach(::wake)
        Executors.newFixedThreadPool(2).use { pool ->
            val results =
                (1..2)
                    .map {
                        pool.submit<Result<ReportExportCleanupResult>> { maintain().execute(4) }
                    }
                    .map { value(it.get(15, TimeUnit.SECONDS)) }
            assertTrue(results.sumOf { it.retired } in 4..5)
            assertEquals(results.sumOf { it.retired }, results.sumOf { it.deletedRows })
        }
        fs.filterNot(::retired).forEach(::wake)
        value(maintain().execute())
        assertTrue(fs.all(::retired))
        assertEquals(
            5,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where action='reporting.export_retired' and resource_id in (select id from report_exports)",
                    Int::class.java,
                ),
        )
    }

    @Test
    fun failedJournalRollsBackJobCancellationRowsRetirementAndTheSchedule() {
        val f = fixture(7)
        val fail =
            object : ChangeJournalRepository by journal {
                override fun record(actor: Actor, change: ChangeRecord): Result<Unit> =
                    Result.Failed(Failure(FailureKind.UNAVAILABLE, "audit_failed"))
            }
        assertTrue(maintain(events = fail).execute() is Result.Failed)
        assertEquals(7, countRows(f))
        assertFalse(retired(f))
        assertEquals(
            false,
            database()
                .queryForObject(
                    "select cancellation_requested from background_jobs where id=?",
                    Boolean::class.java,
                    f.job,
                ),
        )
        assertEquals(
            "QUEUED",
            database()
                .queryForObject(
                    "select status from report_exports where id=?",
                    String::class.java,
                    f.key.id,
                ),
        )
        wake(f)
        assertEquals(ReportExportCleanupResult(1, 7, 1), value(maintain().execute()))
    }

    @Test
    fun discoveryDoesNotGrantGlobalFactsAndSchedulesCannotBeDeletedBeforePhysicalRetirement() {
        val f = fixture(1)
        assertEquals(
            1,
            value(
                    transactions.run(worker()) {
                        expiry.due(clock.instant().truncatedTo(ChronoUnit.MICROS), 4)
                    }
                )
                .size,
        )
        assertEquals(Result.Success(null), transactions.run(worker()) { expiry.inspect(f.key) })
        assertEquals(
            Result.Success(null),
            transactions.run(worker()) {
                snapshots.find(f.key.companyId, f.key.accountId, f.key.id)
            },
        )
        assertTrue(transactions.run(f.actor) { expiry.remove(f.key) } is Result.Failed)
        assertTrue(
            transactions.run(f.actor) { expiry.postpone(f.key, clock.instant().plusSeconds(86400)) }
                is Result.Failed
        )
        assertFalse(retired(f))
        assertEquals(1, value(maintain().execute()).retired)
    }

    @Test
    fun stoppingScheduledCleanupInterruptsAndRollsBackTheOwnedTransaction() {
        val f = fixture(8)
        val entered = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val release = CountDownLatch(1)
        val probe =
            object : ReportExportExpiryRepository by expiry {
                override fun retire(current: ReportExportRetirement, at: Instant): Result<Boolean> {
                    val result = expiry.retire(current, at)
                    entered.countDown()
                    try {
                        check(release.await(5, TimeUnit.SECONDS))
                        return result
                    } finally {
                        exited.countDown()
                    }
                }
            }
        val timer =
            ThreadPoolTaskScheduler().apply {
                poolSize = 1
                setRemoveOnCancelPolicy(true)
                setThreadNamePrefix("export-retention-test-")
                initialize()
            }
        val owner = ReportExportCleanupWorker(maintain(probe), timer, Duration.ofSeconds(1))
        try {
            owner.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val stopped = CountDownLatch(1)
            owner.stop(Runnable { stopped.countDown() })
            assertTrue(stopped.await(3, TimeUnit.SECONDS))
            assertTrue(exited.await(3, TimeUnit.SECONDS))
            assertTrue(timer.scheduledThreadPoolExecutor.awaitTermination(3, TimeUnit.SECONDS))
            assertFalse(owner.isRunning)
            assertTrue(timer.scheduledThreadPoolExecutor.queue.isEmpty())
            assertFalse(retired(f))
            assertEquals(8, countRows(f))
            assertEquals(ReportExportCleanupResult(1, 8, 1), value(maintain().execute()))
            assertThrows(IllegalStateException::class.java) { owner.start() }
            assertFalse(context.containsBean("reportExportCleanupWorker"))
            assertFalse(context.containsBean("reportExportCleanupTimers"))
        } finally {
            release.countDown()
            owner.stop()
        }
    }
}
