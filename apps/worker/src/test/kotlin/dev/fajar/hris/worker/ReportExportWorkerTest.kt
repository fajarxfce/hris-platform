package dev.fajar.hris.worker

import dev.fajar.hris.administration.domain.entities.ClientRequest
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.usecases.LeaseJobs
import dev.fajar.hris.reporting.domain.entities.*
import dev.fajar.hris.reporting.domain.usecases.ManageReportExports
import dev.fajar.hris.storage.domain.entities.*
import dev.fajar.hris.storage.domain.repositories.ObjectStorageRepository
import dev.fajar.hris.worker.runtime.*
import dev.fajar.hris.worker.tasks.ReportExportTask
import java.time.Instant
import java.util.UUID
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

@org.springframework.context.annotation.Import(ReportExportWorkerProviderConfiguration::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["hris.worker.enabled=false"],
)
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS
)
class ReportExportWorkerTest {
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

    @Autowired private lateinit var manage: ManageReportExports
    @Autowired private lateinit var claim: LeaseJobs
    @Autowired private lateinit var task: ReportExportTask
    @Autowired private lateinit var tasks: List<JobTask>
    @Autowired private lateinit var batch: BatchJobExecutor
    @Autowired private lateinit var provider: ReportExportWorkerProvider

    private fun database() =
        JdbcTemplate(
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        )

    private fun <T> value(result: Result<T>): T {
        assertTrue(result is Result.Success, "$result")
        return (result as Result.Success).value
    }

    private fun fixture(): Pair<Actor, UUID> {
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        val now = Instant.now()
        database()
            .update(
                "insert into companies(id,code,name,timezone) values(?,?,'Export worker','UTC')",
                company,
                "E${company.toString().take(8)}",
            )
        database()
            .update(
                "insert into accounts(id,email,display_name,mfa_secret_encrypted) values(?,?,'Export operator','fixture-enrolled')",
                account,
                "$account@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
        for (permission in setOf("reports.read", "payroll.read")) database()
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
                setOf("reports.read", "payroll.read"),
                now.minusSeconds(86400),
                UUID.randomUUID(),
                credentialVersion = 0,
                mfaVerifiedAt = now,
            )
        val receipt =
            value(
                manage.start(
                    actor,
                    UUID.randomUUID(),
                    ReportSelection(
                        ReportKind.PAYROLL_COSTS,
                        "payroll-costs.v1",
                        ReportFilterParameters(from = "2026-10", until = "2026-10"),
                    ),
                    ReportExportDataset.SUMMARY,
                    ClientRequest(null, false),
                )
            )
        return actor to receipt.id
    }

    private fun lease() =
        value(claim.execute(UUID.randomUUID(), 1, 120, setOf(JobKind.REPORT_EXPORT))).single()

    @Test
    fun registeredTaskPublishesThroughRealBatchWithBackgroundAuthorityAndRetainedCleanup() {
        val (actor, id) = fixture()
        val l = lease()
        assertEquals(1, tasks.count { it.kind == JobKind.REPORT_EXPORT })
        assertSame(task, tasks.single { it.kind == JobKind.REPORT_EXPORT })
        assertEquals(Result.Success(Unit), batch.execute(l, task))
        assertEquals(
            "READY",
            database()
                .queryForObject(
                    "select status from report_exports where company_id=? and id=?",
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
                    l.job.request.id,
                ),
        )
        assertEquals(
            3,
            database()
                .queryForObject(
                    "select completed_items from background_jobs where id=?",
                    Int::class.java,
                    l.job.request.id,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from object_cleanup_queue where resource_id=?",
                    Int::class.java,
                    id,
                ),
        )
        assertEquals(
            "COMPLETED",
            database()
                .queryForObject(
                    "select status from hris_batch.batch_job_execution order by job_execution_id desc limit 1",
                    String::class.java,
                ),
        )
        assertTrue(provider.objects.keys.any { it.contains(id.toString()) })
    }

    @Test
    fun credentialChangeBeforeResolutionStillAllowsFencedAbortWithoutContentAccess() {
        val (actor, id) = fixture()
        val l = lease()
        database()
            .update(
                "update accounts set security_version=security_version+1 where id=?",
                actor.accountId,
            )
        val rejected = task.advance(l)
        assertTrue(rejected is Result.Failed)
        assertEquals(Result.Success(Unit), task.abort(l, (rejected as Result.Failed).failure))
        assertEquals(
            "STOPPED",
            database()
                .queryForObject(
                    "select status from report_exports where id=?",
                    String::class.java,
                    id,
                ),
        )
        assertEquals(
            "FAILED",
            database()
                .queryForObject(
                    "select status from background_jobs where id=?",
                    String::class.java,
                    l.job.request.id,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from report_snapshot_rows where snapshot_id=?",
                    Int::class.java,
                    id,
                ),
        )
        assertFalse(provider.objects.keys.any { it.contains(id.toString()) })
    }
}

@org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
class ReportExportWorkerProviderConfiguration {
    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    fun reportExportWorkerProvider() = ReportExportWorkerProvider()
}

class ReportExportWorkerProvider : ObjectStorageRepository {
    val objects = linkedMapOf<String, ByteArray>()

    override fun put(key: String, bytes: ByteArray): Result<StoredObject> {
        assertFalse(
            org.springframework.transaction.support.TransactionSynchronizationManager
                .isActualTransactionActive()
        )
        assertFalse(objects.containsKey(key))
        objects[key] = bytes.copyOf()
        val digest =
            java.util.HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))
        return Result.Success(StoredObject(key, bytes.size.toLong(), "test-etag", digest))
    }

    override fun list(companyId: UUID, afterKey: String?, limit: Int): Result<ObjectInventoryPage> =
        error("Unexpected inventory")

    override fun metadata(key: String): Result<StoredObject?> = error("Unexpected metadata")

    override fun read(key: String, offset: Long, length: Int, etag: String): Result<ByteArray> =
        error("Unexpected read")

    override fun delete(key: String): Result<Unit> = error("Unexpected delete")
}
