package dev.fajar.hris.worker

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.usecases.LeaseJobs
import dev.fajar.hris.people.domain.policies.employeeImportPermissions
import dev.fajar.hris.people.domain.usecases.*
import dev.fajar.hris.worker.runtime.BatchJobExecutor
import dev.fajar.hris.worker.runtime.JobTask
import dev.fajar.hris.worker.tasks.EmployeeImportApplyTask
import dev.fajar.hris.worker.tasks.EmployeeImportPreviewTask
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
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
class EmployeeImportWorkerTest {
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

    @Autowired private lateinit var start: StartEmployeeImport
    @Autowired private lateinit var apply: ApplyEmployeeImport
    @Autowired private lateinit var lease: LeaseJobs
    @Autowired private lateinit var previewTask: EmployeeImportPreviewTask
    @Autowired private lateinit var applyTask: EmployeeImportApplyTask
    @Autowired private lateinit var batch: BatchJobExecutor

    private fun database() =
        JdbcTemplate(
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        )

    private fun fixture(): Pair<Actor, UUID> {
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        val now = Instant.now()
        database()
            .update(
                "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
                company,
                "I${company.toString().take(8)}",
                "Import worker fixture",
            )
        database()
            .update(
                "insert into accounts(id,email,display_name) values(?,?,'Import operator')",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
        for (permission in employeeImportPermissions) database()
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
                employeeImportPermissions,
                now,
                UUID.randomUUID(),
                mfaVerifiedAt = now,
                credentialVersion = 0,
            )
        val id = UUID.randomUUID()
        val csv =
            "employee_number,legal_name,nationality,start_date,contract\nE01,Alya,ID,2026-01-01,PERMANENT\nE02,Raka,ID,2026-01-01,PERMANENT"
        assertTrue(
            start.execute(actor, UUID.randomUUID(), id, "employees.csv", csv, "Worker import test")
                is Result.Success
        )
        return actor to id
    }

    private fun claim(kind: JobKind): JobLease {
        val result = lease.execute(UUID.randomUUID(), 1, 120, setOf(kind))
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value.single()
    }

    @Test
    fun realBatchTasksPreviewThenCreateEmployeesWithRestrictedRuntimeCredentials() {
        val (actor, id) = fixture()
        val preview = claim(JobKind.EMPLOYEE_IMPORT_PREVIEW)
        assertEquals(Result.Success(Unit), batch.execute(preview, previewTask))
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from employments where company_id=?",
                    Int::class.java,
                    actor.companyId,
                ),
        )
        assertTrue(
            apply.execute(actor, UUID.randomUUID(), id, 1, false, "Preview approved")
                is Result.Success
        )
        val applying = claim(JobKind.EMPLOYEE_IMPORT_APPLY)
        assertEquals(Result.Success(Unit), batch.execute(applying, applyTask))
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from employments where company_id=?",
                    Int::class.java,
                    actor.companyId,
                ),
        )
        assertEquals(
            "COMPLETED",
            database()
                .queryForObject(
                    "select status from employee_imports where company_id=? and id=?",
                    String::class.java,
                    actor.companyId,
                    id,
                ),
        )
        assertEquals(
            "SUCCEEDED",
            database()
                .queryForObject(
                    "select status from background_jobs where id=?",
                    String::class.java,
                    applying.job.request.id,
                ),
        )
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from employee_import_rows where company_id=? and import_id=? and status='APPLIED'",
                    Int::class.java,
                    actor.companyId,
                    id,
                ),
        )
    }

    @Test
    fun aNewBatchExecutionResumesAfterTheCommittedRowWithoutRepeatingItsSideEffects() {
        val (actor, id) = fixture()
        assertEquals(
            Result.Success(Unit),
            batch.execute(claim(JobKind.EMPLOYEE_IMPORT_PREVIEW), previewTask),
        )
        assertTrue(
            apply.execute(actor, UUID.randomUUID(), id, 1, false, "Preview approved")
                is Result.Success
        )
        val first = claim(JobKind.EMPLOYEE_IMPORT_APPLY)
        val calls = AtomicInteger()
        val interrupted =
            object : JobTask by applyTask {
                override fun advance(lease: JobLease): Result<JobStep> =
                    if (calls.incrementAndGet() == 1) applyTask.advance(lease)
                    else Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_process_stopped"))
            }
        assertEquals(
            Result.Failed(Failure(FailureKind.UNAVAILABLE, "fixture_process_stopped")),
            batch.execute(first, interrupted),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select completed_items from background_jobs where id=?",
                    Int::class.java,
                    first.job.request.id,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from employments where company_id=?",
                    Int::class.java,
                    actor.companyId,
                ),
        )
        database()
            .update(
                "update background_jobs set lease_until=clock_timestamp()-interval '1 second' where id=?",
                first.job.request.id,
            )
        val resumed = claim(JobKind.EMPLOYEE_IMPORT_APPLY)
        assertNotEquals(first.token, resumed.token)
        assertEquals(1, resumed.job.completedItems)
        assertEquals(Result.Success(Unit), batch.execute(resumed, applyTask))
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from employments where company_id=?",
                    Int::class.java,
                    actor.companyId,
                ),
        )
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from audit_entries where company_id=? and action='people.employee_import_row_applied'",
                    Int::class.java,
                    actor.companyId,
                ),
        )
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select attempts from background_jobs where id=?",
                    Int::class.java,
                    first.job.request.id,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from hris_batch.batch_job_execution where status='FAILED'",
                    Int::class.java,
                ),
        )
    }
}
