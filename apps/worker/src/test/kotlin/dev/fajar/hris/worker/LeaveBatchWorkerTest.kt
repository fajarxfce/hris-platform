package dev.fajar.hris.worker

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.usecases.LeaseJobs
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.usecases.CreateEmployee
import dev.fajar.hris.worker.runtime.BatchJobExecutor
import dev.fajar.hris.worker.runtime.JobTask
import dev.fajar.hris.worker.tasks.LeaveAccrualTask
import dev.fajar.hris.worker.tasks.LeaveYearCloseTask
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
class LeaveBatchWorkerTest {
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
    @Autowired private lateinit var savePolicy: SaveLeaveType
    @Autowired private lateinit var startAccrual: StartLeaveAccrualBatch
    @Autowired private lateinit var startClosing: StartLeaveYearCloseBatch
    @Autowired private lateinit var resume: ResumeLeaveBatch
    @Autowired private lateinit var lease: LeaseJobs
    @Autowired private lateinit var accrualTask: LeaveAccrualTask
    @Autowired private lateinit var closingTask: LeaveYearCloseTask
    @Autowired private lateinit var executor: BatchJobExecutor
    private val companies = mutableSetOf<UUID>()

    private data class Fixture(val actor: Actor, val type: UUID, val period: YearMonth)

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
    }

    private fun fixture(): Fixture {
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        val now = Instant.now()
        companies += company
        database()
            .update(
                "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
                company,
                "L${company.toString().take(8)}",
                "Leave worker fixture",
            )
        database()
            .update(
                "insert into accounts(id,email,display_name) values(?,?,'Leave operator')",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
        for (permission in PermissionCatalog.companyAdministrator) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                company,
                account,
                permission,
            )
        val actor =
            Actor(
                account,
                company,
                PermissionCatalog.companyAdministrator,
                now,
                UUID.randomUUID(),
                mfaVerifiedAt = now,
                credentialVersion = 0,
            )
        val period = YearMonth.of(LocalDate.now(ZoneOffset.UTC).year - 1, 12)
        val start = period.minusYears(1).atDay(1)
        for (number in 1..2) {
            val created =
                createEmployee.execute(
                    actor,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "E$number",
                    PersonProfile(UUID.randomUUID(), null, "Employee $number", null, "ID", null),
                    EmploymentTerms(
                        start,
                        ContractKind.PERMANENT,
                        start,
                        null,
                        EmploymentStatus.ACTIVE,
                        null,
                        null,
                        null,
                        null,
                        null,
                    ),
                    "Worker test employment",
                )
            assertTrue(created is Result.Success, created.toString())
        }
        val type = UUID.randomUUID()
        val policy =
            LeavePolicy(
                "Annual leave",
                true,
                true,
                0,
                setOf(ContractKind.PERMANENT),
                30,
                accrual = LeaveAccrualPolicy(LeaveAccrualFrequency.MONTHLY, 2, 4),
            )
        val saved =
            savePolicy.execute(
                actor,
                UUID.randomUUID(),
                LeaveType(type, "ANNUAL", start, policy, true, 0, 0),
                null,
                "Worker entitlement policy",
            )
        assertTrue(saved is Result.Success, saved.toString())
        return Fixture(actor, type, period)
    }

    private fun begin(f: Fixture): UUID {
        val id = UUID.randomUUID()
        val result =
            startAccrual.execute(
                f.actor,
                UUID.randomUUID(),
                id,
                f.type,
                f.period,
                0,
                null,
                "Monthly company entitlement",
            )
        assertTrue(result is Result.Success, result.toString())
        return id
    }

    private fun claim(kind: JobKind = JobKind.LEAVE_ACCRUAL): JobLease {
        val result = lease.execute(UUID.randomUUID(), 1, 120, setOf(kind))
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value.single()
    }

    private fun count(f: Fixture, table: String): Int =
        database()
            .queryForObject(
                "select count(*) from $table where company_id=?",
                Int::class.java,
                f.actor.companyId,
            )!!

    @Test
    fun realWorkerTasksAccrueAndCloseBalancesUsingRestrictedRuntimeCredentials() {
        val f = fixture()
        val id = begin(f)
        val grant = claim()
        assertEquals(id.toString(), grant.job.request.values["batchId"])
        assertEquals(Result.Success(Unit), executor.execute(grant, accrualTask))
        assertEquals(2, count(f, "leave_accrual_postings"))
        val closing = UUID.randomUUID()
        val created =
            startClosing.execute(
                f.actor,
                UUID.randomUUID(),
                closing,
                f.type,
                f.period.year,
                0,
                null,
                "Annual company closing",
            )
        assertTrue(created is Result.Success, created.toString())
        assertEquals(
            Result.Success(Unit),
            executor.execute(claim(JobKind.LEAVE_YEAR_CLOSE), closingTask),
        )
        assertEquals(2, count(f, "leave_year_closings"))
        assertEquals(6, count(f, "leave_ledger"))
        assertEquals(4, count(f, "leave_accounts"))
        assertEquals(4, count(f, "leave_batch_results"))
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from leave_batches where company_id=? and status='COMPLETED'",
                    Int::class.java,
                    f.actor.companyId,
                ),
        )
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from leave_accounts where company_id=? and closing_id is not null",
                    Int::class.java,
                    f.actor.companyId,
                ),
        )
    }

    @Test
    fun aNewSpringBatchExecutionContinuesAfterACommittedEmployeeWithoutDuplicatingGrant() {
        val f = fixture()
        begin(f)
        val first = claim()
        val calls = AtomicInteger()
        val stopped =
            object : JobTask by accrualTask {
                override fun advance(lease: JobLease): Result<JobStep> =
                    if (calls.incrementAndGet() == 1) accrualTask.advance(lease)
                    else Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_process_stopped"))
            }
        assertEquals(
            Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_process_stopped")),
            executor.execute(first, stopped),
        )
        assertEquals(1, count(f, "leave_accrual_postings"))
        database()
            .update(
                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                first.job.request.id,
            )
        val next = claim()
        assertNotEquals(first.token, next.token)
        assertEquals(1, next.job.completedItems)
        assertEquals(Result.Success(Unit), executor.execute(next, accrualTask))
        assertEquals(2, count(f, "leave_accrual_postings"))
        assertEquals(2, count(f, "leave_batch_results"))
        assertEquals(1, count(f, "leave_batch_attempts"))
    }

    @Test
    fun exhaustedProcessLeasesCanBeExplicitlyResumedWithoutRewindingCommittedWork() {
        val f = fixture()
        val id = begin(f)
        val first = claim()
        assertEquals(Result.Success(JobStep(1, false)), accrualTask.advance(first))
        database()
            .update(
                "update background_jobs set attempts=8,lease_until=clock_timestamp()-interval '1 second' where id=?",
                first.job.request.id,
            )
        val exhausted = lease.execute(UUID.randomUUID(), 1, 120, setOf(JobKind.LEAVE_ACCRUAL))
        assertEquals(Result.Success(emptyList<JobLease>()), exhausted)
        assertEquals(
            "FAILED",
            database()
                .queryForObject(
                    "select status from background_jobs where id=?",
                    String::class.java,
                    first.job.request.id,
                ),
        )
        assertEquals(
            "RUNNING",
            database()
                .queryForObject(
                    "select status from leave_batches where company_id=? and id=?",
                    String::class.java,
                    f.actor.companyId,
                    id,
                ),
        )
        val resumed =
            resume.execute(f.actor, UUID.randomUUID(), id, 0, "Recover exhausted process attempts")
        assertTrue(resumed is Result.Success, resumed.toString())
        val next = claim()
        assertNotEquals(first.job.request.id, next.job.request.id)
        assertEquals(2, next.job.request.totalItems)
        assertEquals(Result.Success(Unit), executor.execute(next, accrualTask))
        assertEquals(2, count(f, "leave_accrual_postings"))
        assertEquals(2, count(f, "leave_batch_attempts"))
    }
}
