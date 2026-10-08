package dev.fajar.hris.worker

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.jobs.domain.usecases.*
import dev.fajar.hris.worker.runtime.*
import dev.fajar.hris.worker.tasks.WorkPeriodCloseTask
import dev.fajar.hris.workforce.domain.usecases.StartWorkPeriodClose
import java.time.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
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
class WorkerIntegrationTest {
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

    @Autowired private lateinit var start: StartWorkPeriodClose
    @Autowired private lateinit var lease: LeaseJobs
    @Autowired private lateinit var heartbeat: KeepJobAlive
    @Autowired private lateinit var defer: DeferJob
    @Autowired private lateinit var task: WorkPeriodCloseTask
    @Autowired private lateinit var batch: BatchJobExecutor
    @Autowired private lateinit var runner: JobRunExecutor
    @Autowired private lateinit var jobs: JobRepository
    @Autowired private lateinit var transactions: TransactionRunner
    private val companies = mutableListOf<UUID>()

    private fun database() =
        JdbcTemplate(
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        )

    @AfterEach
    fun settleJobs() {
        companies.forEach {
            database()
                .update(
                    """update background_jobs set status='CANCELLED',finished_at=now(),lease_owner=null,lease_token=null,lease_until=null
            where company_id=? and status in ('QUEUED','RUNNING')""",
                    it,
                )
        }
    }

    private fun fixture(): Actor {
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        companies += company
        database()
            .update(
                "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
                company,
                "W${company.toString().take(8)}",
                "Worker fixture",
            )
        database()
            .update(
                "insert into accounts(id,email,display_name) values(?,?,'Worker operator')",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'workforce.close')",
                company,
                account,
            )
        val actor =
            Actor(
                account,
                company,
                setOf("workforce.close"),
                Instant.now(),
                UUID.randomUUID(),
                credentialVersion = 0,
            )
        val response =
            start.execute(
                actor,
                UUID.randomUUID(),
                YearMonth.of(2026, 1),
                0,
                "Worker integration test",
            )
        assertTrue(response is Result.Success, response.toString())
        return actor
    }

    private fun claim(): JobLease {
        val claimed = lease.execute(UUID.randomUUID(), 1, 60, setOf(JobKind.WORKFORCE_CLOSE))
        assertTrue(claimed is Result.Success, claimed.toString())
        return (claimed as Result.Success).value.single()
    }

    @Test
    fun realBatchTaskCommitsTheCompanySnapshotAndPersistentBatchMetadata() {
        val actor = fixture()
        val leased = claim()
        assertEquals(Result.Success(Unit), batch.execute(leased, task))
        assertEquals(
            "CLOSED",
            database()
                .queryForObject(
                    "select status from work_periods where company_id=?",
                    String::class.java,
                    actor.companyId,
                ),
        )
        assertEquals(
            "SUCCEEDED",
            database()
                .queryForObject(
                    "select status from background_jobs where id=?",
                    String::class.java,
                    leased.job.request.id,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    """select count(*) from hris_batch.batch_job_execution e
            join hris_batch.batch_job_execution_params p on p.job_execution_id=e.job_execution_id
            where p.parameter_name='jobId' and p.parameter_value=? and e.status='COMPLETED'""",
                    Int::class.java,
                    leased.job.request.id.toString(),
                ),
        )
        assertEquals(Result.Success(LeaseHealth.FINISHED), heartbeat.execute(leased, 60))
    }

    @Test
    fun revokedOriginStopsBusinessWorkButFencedCleanupRemainsPossible() {
        val actor = fixture()
        val leased = claim()
        database().update("update accounts set active=false where id=?", actor.accountId)
        runner.execute(RunningJob(leased), task)
        assertEquals(
            "REVIEW_REQUIRED",
            database()
                .queryForObject(
                    "select status from work_periods where company_id=?",
                    String::class.java,
                    actor.companyId,
                ),
        )
        assertEquals(
            "session_revoked",
            database()
                .queryForObject(
                    "select failure_code from background_jobs where id=?",
                    String::class.java,
                    leased.job.request.id,
                ),
        )
        assertEquals(
            "FAILED",
            database()
                .queryForObject(
                    "select status from background_jobs where id=?",
                    String::class.java,
                    leased.job.request.id,
                ),
        )
    }

    @Test
    fun nonProgressingAdapterTerminatesAfterOneStep() {
        fixture()
        val leased = claim()
        val calls = AtomicInteger()
        val stuck =
            object : JobTask by task {
                override fun advance(lease: JobLease): Result<JobStep> {
                    calls.incrementAndGet()
                    return Result.Success(JobStep(0, false))
                }
            }
        runner.execute(RunningJob(leased), stuck)
        assertEquals(1, calls.get())
        assertEquals(
            "job_did_not_progress",
            database()
                .queryForObject(
                    "select failure_code from background_jobs where id=?",
                    String::class.java,
                    leased.job.request.id,
                ),
        )
    }

    @Test
    fun shutdownInterruptsOwnedWorkAndReleasesEveryWorkerThread() {
        fixture()
        val entered = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val blocking =
            object : JobTask by task {
                override fun advance(lease: JobLease): Result<JobStep> {
                    entered.countDown()
                    try {
                        check(CountDownLatch(1).await(30, TimeUnit.SECONDS))
                        return Result.Success(JobStep(1, true))
                    } catch (error: InterruptedException) {
                        interrupted.countDown()
                        throw error
                    }
                }
            }
        val settings =
            WorkerSettings(
                pollInterval = Duration.ofMillis(10),
                heartbeatInterval = Duration.ofMillis(50),
                shutdownTimeout = Duration.ofSeconds(5),
            )
        val scheduler = JobScheduler(listOf(blocking), lease, heartbeat, defer, runner, settings)
        scheduler.start()
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
        } finally {
            scheduler.stop()
        }
        assertTrue(interrupted.await(2, TimeUnit.SECONDS))
        assertFalse(scheduler.isRunning)
        assertTrue(
            Thread.getAllStackTraces().keys.none {
                it.isAlive &&
                    (it.name.startsWith("hris-job-") || it.name.startsWith("hris-worker-control-"))
            }
        )
        assertEquals(
            "QUEUED",
            database()
                .queryForObject(
                    "select status from background_jobs where company_id=?",
                    String::class.java,
                    companies.single(),
                ),
        )
        assertThrows(IllegalStateException::class.java) { scheduler.start() }
    }

    @Test
    fun lateCancellationCannotInterruptAThreadThatHasMovedToAnotherJob() {
        fixture()
        val leased = claim()
        val run = RunningJob(leased)
        val detached = CountDownLatch(1)
        val release = CountDownLatch(1)
        Executors.newSingleThreadExecutor().use { pool ->
            val result =
                pool.submit<Boolean> {
                    run.attachThread()
                    run.detachThread()
                    detached.countDown()
                    release.await(5, TimeUnit.SECONDS) && !Thread.currentThread().isInterrupted
                }
            assertTrue(detached.await(5, TimeUnit.SECONDS))
            run.cancel(JobCancellation.REQUESTED)
            release.countDown()
            assertTrue(result.get(5, TimeUnit.SECONDS))
        }
    }
}
