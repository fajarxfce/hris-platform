package dev.fajar.hris.worker

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.usecases.LeaseJobs
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.policies.*
import dev.fajar.hris.payroll.domain.usecases.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.usecases.CreateEmployee
import dev.fajar.hris.worker.runtime.*
import dev.fajar.hris.worker.tasks.*
import dev.fajar.hris.workforce.domain.usecases.StartWorkPeriodClose
import java.math.BigDecimal
import java.time.*
import java.util.UUID
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
class PayrollCalculationWorkerTest {
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

    @Autowired private lateinit var createEmployee: CreateEmployee
    @Autowired private lateinit var savePolicy: SavePayrollPolicy
    @Autowired private lateinit var createPeriod: CreatePayrollPeriod
    @Autowired private lateinit var start: StartPayrollCalculation
    @Autowired private lateinit var resume: ResumePayrollCalculation
    @Autowired private lateinit var get: GetPayrollRun
    @Autowired private lateinit var startWork: StartWorkPeriodClose
    @Autowired private lateinit var workTask: WorkPeriodCloseTask
    @Autowired private lateinit var task: PayrollCalculationTask
    @Autowired private lateinit var lease: LeaseJobs
    @Autowired private lateinit var batch: BatchJobExecutor
    @Autowired private lateinit var runner: JobRunExecutor
    private val companies = mutableSetOf<UUID>()

    private data class Fixture(val actor: Actor, val id: UUID)

    private fun database() =
        JdbcTemplate(
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        )

    @AfterEach
    fun cleanup() {
        for (company in companies) database()
            .update(
                "update background_jobs set status='CANCELLED',finished_at=now(),lease_owner=null,lease_token=null,lease_until=null where company_id=? and status in ('QUEUED','RUNNING')",
                company,
            )
        companies.clear()
    }

    private fun claim(kind: JobKind = JobKind.PAYROLL_CALCULATE): JobLease {
        val result = lease.execute(UUID.randomUUID(), 1, 120, setOf(kind))
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value.single()
    }

    private fun fixture(): Fixture {
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        companies += company
        database()
            .update(
                "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
                company,
                "P${company.toString().take(8)}",
                "Payroll worker fixture",
            )
        database()
            .update(
                "insert into accounts(id,email,display_name,mfa_secret_encrypted) values(?,?,'Payroll operator','fixture-enrolled')",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
        val permissions =
            PermissionCatalog.companyAdministrator +
                setOf(
                    "payroll.calculate",
                    "payroll.review",
                    "payroll.policy.manage",
                    "payroll.read",
                )
        for (permission in permissions) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                company,
                account,
                permission,
            )
        val now = Instant.now()
        val actor =
            Actor(
                account,
                company,
                permissions,
                now,
                UUID.randomUUID(),
                mfaVerifiedAt = now,
                credentialVersion = 0,
            )
        val month = YearMonth.from(LocalDate.now(ZoneOffset.UTC)).minusMonths(1)
        val employees =
            (1..2)
                .map { n ->
                    val id = UUID.randomUUID()
                    val first = month.atDay(1).minusYears(1)
                    val result =
                        createEmployee.execute(
                            actor,
                            UUID.randomUUID(),
                            id,
                            "E$n",
                            PersonProfile(UUID.randomUUID(), null, "Employee $n", null, "ID", null),
                            EmploymentTerms(
                                first,
                                ContractKind.PERMANENT,
                                first,
                                null,
                                EmploymentStatus.ACTIVE,
                                null,
                                null,
                                null,
                                null,
                                null,
                            ),
                            "Owned employee fixture",
                        )
                    assertTrue(result is Result.Success, result.toString())
                    id
                }
                .toSet()
        val closed =
            startWork.execute(actor, UUID.randomUUID(), month, 0, "Close workforce evidence")
        assertTrue(closed is Result.Success, closed.toString())
        assertEquals(Result.Success(Unit), batch.execute(claim(JobKind.WORKFORCE_CLOSE), workTask))
        val workVersion =
            database()
                .queryForObject(
                    "select version from work_periods where company_id=?",
                    Long::class.java,
                    company,
                )!!
        val policy =
            PayrollPolicy(
                0,
                0,
                YearMonth.of(month.year, 1),
                YearMonth.of(month.year, 12),
                INDONESIAN_INCOME_TAX_2024,
                INDONESIAN_INSURANCE_PU_V1,
                BigDecimal("4000000"),
                BigDecimal("12000000"),
                BigDecimal("10000000"),
                ContributionRounding.HALF_UP,
                listOf("https://example.test/fictional-worker-policy"),
            )
        val saved =
            savePolicy.execute(actor, UUID.randomUUID(), policy, null, "Reviewed policy fixture")
        assertTrue(saved is Result.Success, saved.toString())
        val period = UUID.randomUUID()
        val created =
            createPeriod.execute(
                actor,
                UUID.randomUUID(),
                period,
                month,
                month.atEndOfMonth(),
                employees,
                "Prepare monthly payroll",
            )
        assertTrue(created is Result.Success, created.toString())
        val id = UUID.randomUUID()
        val started =
            start.execute(
                actor,
                UUID.randomUUID(),
                id,
                period,
                month.atEndOfMonth(),
                0,
                workVersion,
                0,
                "Reviewed monthly income liability",
                "Begin payroll calculation",
            )
        assertTrue(started is Result.Success, started.toString())
        return Fixture(actor, id)
    }

    private fun view(f: Fixture): PayrollRunDetails {
        val result = get.execute(f.actor, f.id, null, 50)
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    @Test
    fun aRealBatchExecutionRetainsPerEmployeeReadinessFailuresAndCompletesMetadata() {
        val f = fixture()
        assertEquals(Result.Success(Unit), batch.execute(claim(), task))
        val detail = view(f)
        assertEquals(PayrollRunStatus.CALCULATED, detail.run.status)
        assertEquals(2, detail.run.failed)
        assertEquals(3, detail.job.completedItems)
        assertEquals(
            listOf("payroll_compensation_required", "payroll_compensation_required"),
            detail.results.items.map { it.failure?.code },
        )
        assertEquals(
            false,
            database()
                .queryForObject(
                    "select rolbypassrls from pg_roles where rolname='hris_worker_test'",
                    Boolean::class.java,
                ),
        )
    }

    @Test
    fun aNewBatchExecutionUsesTheCommittedCheckpointAfterProcessInterruption() {
        val f = fixture()
        val first = claim()
        val calls = AtomicInteger()
        val stopped =
            object : JobTask by task {
                override fun advance(lease: JobLease): Result<JobStep> =
                    if (calls.incrementAndGet() == 1) task.advance(lease)
                    else Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_process_stopped"))
            }
        assertEquals(
            Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_process_stopped")),
            batch.execute(first, stopped),
        )
        val retained = view(f).results.items.single()
        database()
            .update(
                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                first.job.request.id,
            )
        val next = claim()
        assertNotEquals(first.token, next.token)
        assertEquals(1, next.job.completedItems)
        assertEquals(Result.Success(Unit), batch.execute(next, task))
        val detail = view(f)
        assertEquals(2, detail.results.items.size)
        assertEquals(retained, detail.results.items.first())
        assertEquals(1, detail.attempts.size)
    }

    @Test
    fun revocationStopsTheRunAndANewCredentialCanExplicitlyResumeIt() {
        val f = fixture()
        val first = claim()
        database()
            .update(
                "update accounts set security_version=security_version+1 where id=?",
                f.actor.accountId,
            )
        runner.execute(RunningJob(first), task)
        val renewed =
            f.copy(
                actor =
                    f.actor.copy(
                        credentialVersion = 1,
                        authenticatedAt = Instant.now(),
                        mfaVerifiedAt = Instant.now(),
                    )
            )
        val stopped = view(renewed)
        assertEquals(PayrollRunStatus.STOPPED, stopped.run.status)
        assertEquals(JobStatus.FAILED, stopped.job.status)
        assertEquals(0, stopped.run.processed)
        val resumed =
            resume.execute(
                renewed.actor,
                UUID.randomUUID(),
                f.id,
                stopped.run.version,
                "Renewed payroll authority",
            )
        assertTrue(resumed is Result.Success, resumed.toString())
        assertEquals(Result.Success(Unit), batch.execute(claim(), task))
        assertEquals(2, view(renewed).run.processed)
    }
}
