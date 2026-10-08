package dev.fajar.hris

import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.database.datasources.PostgresChangeJournalDataSource
import dev.fajar.hris.core.database.repositories.PostgresChangeJournalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.data.datasources.PostgresJobDataSource
import dev.fajar.hris.jobs.data.repositories.PostgresJobRepository
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.repositories.JobRepository
import dev.fajar.hris.jobs.domain.usecases.LeaseJobs
import java.net.http.HttpClient
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager

class JobQueueHttpTest : ApiIntegrationTest() {
    @Autowired private lateinit var jobs: JobRepository
    @Autowired private lateinit var transactions: TransactionRunner
    private val created = mutableListOf<UUID>()

    private data class Fixture(val client: HttpClient, val csrf: String, val actor: Actor)

    private data class Worker(
        val jobs: JobRepository,
        val transactions: TransactionRunner,
        val jdbc: JdbcTemplate,
        val leaseJobs: LeaseJobs,
    )

    @BeforeEach
    fun prepareWorker() {
        database()
            .execute(
                """DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='hris_test_worker') THEN
            CREATE ROLE hris_test_worker LOGIN PASSWORD 'test-worker-only' INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
            END IF; END $$"""
            )
        database().execute("GRANT hris_worker_capability TO hris_test_worker")
        database().execute("GRANT USAGE ON SCHEMA public TO hris_test_worker")
        database()
            .execute(
                "GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO hris_test_worker"
            )
    }

    @AfterEach
    fun closeFixtureJobs() {
        created.forEach {
            database()
                .update(
                    """update background_jobs set status='CANCELLED',finished_at=now(),lease_owner=null,lease_token=null,lease_until=null
                where id=? and status in ('QUEUED','RUNNING')""",
                    it,
                )
        }
    }

    private fun fixture(): Fixture {
        val client = client()
        val csrf = login(client)
        val account =
            UUID.fromString(
                json.readTree(get(client, "/api/v1/me").body()).get("account").get("id").asString()
            )
        val company =
            command(
                client,
                "/api/v1/companies",
                """{"code":"J${UUID.randomUUID().toString().take(8)}","name":"Queue test","timezone":"UTC"}""",
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, company.statusCode(), company.body())
        val companyId = UUID.fromString(json.readTree(company.body()).get("id").asString())
        return Fixture(
            client,
            csrf,
            Actor(
                account,
                companyId,
                setOf("jobs.read", "jobs.manage"),
                Instant.now(),
                UUID.randomUUID(),
                credentialVersion = 0,
            ),
        )
    }

    private fun create(f: Fixture, total: Int = 2): BackgroundJob {
        val request =
            JobRequest(
                UUID.randomUUID(),
                requireNotNull(f.actor.companyId),
                f.actor.accountId,
                JobKind.WORKFORCE_CLOSE,
                UUID.randomUUID(),
                mapOf("period" to "2026-09"),
                f.actor.authenticatedAt,
                0,
                f.actor.correlationId,
                Instant.now(),
                total,
            )
        val result = transactions.run(f.actor) { jobs.create(request) }
        assertTrue(result is Result.Success, result.toString())
        created += request.id
        return (result as Result.Success).value
    }

    private fun worker(): Worker {
        val source =
            DriverManagerDataSource(postgres.jdbcUrl, "hris_test_worker", "test-worker-only")
        val jdbc = JdbcTemplate(source)
        val sql =
            DSL.using(
                TransactionAwareDataSourceProxy(source),
                SQLDialect.POSTGRES,
                Settings().withExecuteLogging(false),
            )
        val repository = PostgresJobRepository(PostgresJobDataSource(sql), json)
        val tx = PostgresTransactionRunner(JdbcTransactionManager(source), jdbc)
        return Worker(
            repository,
            tx,
            jdbc,
            LeaseJobs(
                repository,
                tx,
                PostgresChangeJournalRepository(PostgresChangeJournalDataSource(sql, json)),
                java.time.Clock.systemUTC(),
                JobRetryPolicy(),
            ),
        )
    }

    private fun claim(
        worker: Worker,
        owner: UUID = UUID.randomUUID(),
        size: Int = 2,
    ): List<JobLease> {
        val result = worker.leaseJobs.execute(owner, size, 60, setOf(JobKind.WORKFORCE_CLOSE))
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    @Test
    fun onlyWorkerCredentialsCanLeaseAndCompetingWorkersRespectGlobalAndCompanyLimits() {
        val first = fixture()
        val second = fixture()
        repeat(4) {
            create(first)
            create(second)
        }
        val rejected = transactions.run(first.actor) { jobs.claim(UUID.randomUUID(), 2, 60) }
        assertTrue(rejected is Result.Failed)
        assertEquals(FailureKind.FORBIDDEN, (rejected as Result.Failed).failure.kind)
        val workers = listOf(worker(), worker())
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val leases =
            Executors.newFixedThreadPool(2)
                .use { pool ->
                    val calls =
                        workers.map { worker ->
                            pool.submit<List<JobLease>> {
                                ready.countDown()
                                check(start.await(5, TimeUnit.SECONDS))
                                claim(worker)
                            }
                        }
                    assertTrue(ready.await(5, TimeUnit.SECONDS))
                    start.countDown()
                    calls.flatMap { it.get(15, TimeUnit.SECONDS) }
                }
                .toMutableList()
        leases += claim(workers.first())
        assertEquals(4, leases.size)
        assertEquals(4, leases.map { it.job.request.id }.toSet().size)
        assertTrue(
            leases.groupingBy { it.job.request.companyId }.eachCount().values.all { it <= 2 }
        )
        assertTrue(claim(workers.last()).isEmpty())
        database()
            .update(
                "insert into organization_units(company_id,id,code,name,kind) values(?,?,'ISOLATED','Private branch','BRANCH')",
                second.actor.companyId,
                UUID.randomUUID(),
            )
        val isolated =
            workers.first().transactions.run(first.actor) {
                Result.Success(
                    workers
                        .first()
                        .jdbc
                        .queryForObject(
                            "select count(*) from organization_units where company_id=?",
                            Int::class.java,
                            second.actor.companyId,
                        )
                )
            }
        assertEquals(Result.Success(0), isolated)
        val scoped =
            transactions.run(first.actor) {
                jobs.find(
                    requireNotNull(second.actor.companyId),
                    leases
                        .first { it.job.request.companyId == second.actor.companyId }
                        .job
                        .request
                        .id,
                )
            }
        assertEquals(Result.Success<BackgroundJob?>(null), scoped)
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from pg_roles where rolname='hris_test_worker' and (rolsuper or rolbypassrls)",
                    Int::class.java,
                ),
        )
        val runtime =
            JdbcTemplate(
                DriverManagerDataSource(postgres.jdbcUrl, "hris_test_runtime", "test-runtime-only")
            )
        assertThrows(org.springframework.dao.DataAccessException::class.java) {
            runtime.queryForObject(
                "select count(*) from hris_batch.batch_job_instance",
                Int::class.java,
            )
        }
        assertEquals(
            0,
            workers
                .first()
                .jdbc
                .queryForObject(
                    "select count(*) from hris_batch.batch_job_instance",
                    Int::class.java,
                ),
        )
    }

    @Test
    fun expiredLeasesRejectLateProgressAndBusinessWritesRollBackWithTheFailedFence() {
        val f = fixture()
        val job = create(f)
        val worker = worker()
        val old = claim(worker).single()
        database()
            .update(
                "update background_jobs set lease_until=now()-interval '1 second' where id=?",
                job.request.id,
            )
        val next = claim(worker).single()
        assertNotEquals(old.token, next.token)
        assertEquals(2, next.job.attempts)
        val late =
            worker.transactions.run(f.actor) {
                worker.jdbc.update(
                    "update companies set name='Stale worker' where id=?",
                    f.actor.companyId,
                )
                worker.jobs.checkpoint(old, JobProgress(1, mapOf("cursor" to "late"))).flatMap {
                    applied ->
                    if (applied) Result.Success(Unit)
                    else Result.Failed(Failure(FailureKind.CONFLICT, "job_lease_lost"))
                }
            }
        assertTrue(late is Result.Failed)
        assertEquals(
            "Queue test",
            database()
                .queryForObject(
                    "select name from companies where id=?",
                    String::class.java,
                    f.actor.companyId,
                ),
        )
        assertEquals(
            Result.Success(false),
            worker.transactions.run(f.actor) { worker.jobs.renew(old, 60) },
        )
        assertEquals(
            Result.Success(true),
            worker.transactions.run(f.actor) {
                worker.jobs.checkpoint(next, JobProgress(1, mapOf("cursor" to "first")))
            },
        )
        assertEquals(
            Result.Success(false),
            worker.transactions.run(f.actor) {
                worker.jobs.checkpoint(next, JobProgress(0, emptyMap()))
            },
        )
        assertEquals(
            Result.Success(false),
            worker.transactions.run(f.actor) { worker.jobs.complete(next, JobStatus.SUCCEEDED) },
        )
        assertEquals(
            Result.Success(true),
            worker.transactions.run(f.actor) {
                worker.jobs.checkpoint(next, JobProgress(2, mapOf("cursor" to "last")))
            },
        )
        assertEquals(
            Result.Success(true),
            worker.transactions.run(f.actor) { worker.jobs.complete(next, JobStatus.SUCCEEDED) },
        )
        assertThrows(org.springframework.dao.DataAccessException::class.java) {
            database()
                .update("update background_jobs set status='QUEUED' where id=?", job.request.id)
        }
        assertEquals(
            Result.Success(false),
            worker.transactions.run(f.actor) { worker.jobs.complete(next, JobStatus.SUCCEEDED) },
        )
    }

    @Test
    fun cancellationIsVersionedAndStopsProgressBeforeTheWorkerAcknowledgesIt() {
        val f = fixture()
        val job = create(f)
        val worker = worker()
        val lease = claim(worker).single()
        val path = "/api/v1/companies/${f.actor.companyId}/jobs/${job.request.id}"
        val detail = get(f.client, path)
        assertEquals(200, detail.statusCode(), detail.body())
        assertFalse(detail.body().contains("credentialVersion"))
        assertFalse(detail.body().contains("authenticatedAt"))
        val version = json.readTree(detail.body()).get("version").asLong()
        assertEquals(
            409,
            post(f.client, "$path/cancel", """{"expectedVersion":${version+1}}""", f.csrf)
                .statusCode(),
        )
        val accepted = post(f.client, "$path/cancel", """{"expectedVersion":$version}""", f.csrf)
        assertEquals(200, accepted.statusCode(), accepted.body())
        assertEquals(
            accepted.body(),
            post(f.client, "$path/cancel", """{"expectedVersion":$version}""", f.csrf).body(),
        )
        assertEquals(
            Result.Success(false),
            worker.transactions.run(f.actor) {
                worker.jobs.checkpoint(lease, JobProgress(1, emptyMap()))
            },
        )
        assertEquals(
            Result.Success(false),
            worker.transactions.run(f.actor) { worker.jobs.complete(lease, JobStatus.SUCCEEDED) },
        )
        assertEquals(
            Result.Success(true),
            worker.transactions.run(f.actor) { worker.jobs.complete(lease, JobStatus.CANCELLED) },
        )
        assertEquals(
            "CANCELLED",
            json.readTree(get(f.client, path).body()).get("status").asString(),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='jobs.cancellation_requested'",
                    Int::class.java,
                    job.request.id,
                ),
        )
    }

    @Test
    fun repeatedCrashesStopAfterTheAttemptCeilingAndPreserveTheFailure() {
        val f = fixture()
        val job = create(f)
        database().update("update background_jobs set attempts=7 where id=?", job.request.id)
        val worker = worker()
        val lease = claim(worker).single()
        assertEquals(8, lease.job.attempts)
        assertEquals(
            Result.Success(false),
            worker.transactions.run(f.actor) { worker.jobs.defer(lease, 5, "transient_failure") },
        )
        database()
            .update(
                "update background_jobs set lease_until=now()-interval '1 second' where id=?",
                job.request.id,
            )
        assertTrue(claim(worker).isEmpty())
        assertEquals(
            "FAILED",
            database()
                .queryForObject(
                    "select status from background_jobs where id=?",
                    String::class.java,
                    job.request.id,
                ),
        )
        assertEquals(
            "job_attempts_exhausted",
            database()
                .queryForObject(
                    "select failure_code from background_jobs where id=?",
                    String::class.java,
                    job.request.id,
                ),
        )
        assertTrue(claim(worker).isEmpty())
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where resource_id=? and action='jobs.attempts_exhausted'",
                    Int::class.java,
                    job.request.id,
                ),
        )
    }
}
