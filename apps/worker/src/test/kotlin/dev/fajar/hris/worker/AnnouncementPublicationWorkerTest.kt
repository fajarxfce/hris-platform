package dev.fajar.hris.worker

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.communications.domain.usecases.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.usecases.LeaseJobs
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.usecases.CreateEmployee
import dev.fajar.hris.worker.runtime.BatchJobExecutor
import dev.fajar.hris.worker.tasks.AnnouncementPublicationTask
import java.time.*
import java.util.UUID
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
class AnnouncementPublicationWorkerTest {
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

    @Autowired private lateinit var employees: CreateEmployee
    @Autowired private lateinit var save: SaveAnnouncement
    @Autowired private lateinit var publish: QueueAnnouncement
    @Autowired private lateinit var leases: LeaseJobs
    @Autowired private lateinit var task: AnnouncementPublicationTask
    @Autowired private lateinit var executor: BatchJobExecutor
    private val companies = mutableSetOf<UUID>()

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

    private fun fixture(): Actor {
        val company = UUID.randomUUID()
        val account = UUID.randomUUID()
        companies += company
        val db = database()
        db.update(
            "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
            company,
            "C${company.toString().take(8)}",
            "Communication worker fixture",
        )
        db.update(
            "insert into accounts(id,email,display_name) values(?,?,'Communication operator')",
            account,
            "$account@example.test",
        )
        db.update(
            "insert into company_memberships(company_id,account_id) values(?,?)",
            company,
            account,
        )
        for (permission in PermissionCatalog.companyAdministrator) db.update(
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
                Instant.now(),
                UUID.randomUUID(),
                mfaVerifiedAt = Instant.now(),
                credentialVersion = 0,
            )
        val recipient = UUID.randomUUID()
        db.update(
            "insert into accounts(id,email,display_name) values(?,?,'Communication recipient')",
            recipient,
            "$recipient@example.test",
        )
        db.update(
            "insert into company_memberships(company_id,account_id) values(?,?)",
            company,
            recipient,
        )
        db.update(
            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'announcements.read')",
            company,
            recipient,
        )
        val start = LocalDate.now(ZoneOffset.UTC).minusDays(1)
        val created =
            employees.execute(
                actor,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "COMM001",
                PersonProfile(
                    UUID.randomUUID(),
                    recipient,
                    "Communication employee",
                    null,
                    "ID",
                    null,
                ),
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
                "Worker communication fixture",
            )
        assertTrue(created is Result.Success, created.toString())
        return actor
    }

    private fun queue(actor: Actor): JobLease {
        val id = UUID.randomUUID()
        val saved =
            save.execute(
                actor,
                UUID.randomUUID(),
                SaveAnnouncementCommand(
                    id,
                    null,
                    "Office update",
                    "Please review the office update.",
                    AnnouncementAudience(AudienceKind.COMPANY),
                    true,
                    "Worker publication",
                ),
            )
        assertTrue(saved is Result.Success, saved.toString())
        val queued = publish.execute(actor, UUID.randomUUID(), id, 0, null, "Publish update")
        assertTrue(queued is Result.Success, queued.toString())
        val claim = leases.execute(UUID.randomUUID(), 1, 120, setOf(JobKind.ANNOUNCEMENT_PUBLISH))
        assertTrue(claim is Result.Success, claim.toString())
        return (claim as Result.Success).value.single()
    }

    @Test
    fun springBatchPublishesOneImmutableInboxUsingRestrictedWorkerCredentials() {
        val actor = fixture()
        val lease = queue(actor)
        assertEquals(Result.Success(Unit), executor.execute(lease, task))
        val db = database()
        assertEquals(
            "SUCCEEDED",
            db.queryForObject(
                "select status from background_jobs where id=?",
                String::class.java,
                lease.job.request.id,
            ),
        )
        assertEquals(
            1,
            db.queryForObject(
                "select count(*) from inbox_items where company_id=?",
                Int::class.java,
                actor.companyId,
            ),
        )
        assertEquals(
            1,
            db.queryForObject(
                "select count(*) from announcement_publications where company_id=?",
                Int::class.java,
                actor.companyId,
            ),
        )
        val stale = task.advance(lease)
        assertEquals("job_lease_lost", (stale as Result.Failed).failure.code)
    }

    @Test
    fun credentialRevocationStopsExecutionAndCleanupDoesNotRequireRevokedPermission() {
        val actor = fixture()
        val lease = queue(actor)
        database()
            .update(
                "update accounts set security_version=security_version+1 where id=?",
                actor.accountId,
            )
        val failed = executor.execute(lease, task)
        assertTrue(failed is Result.Failed, failed.toString())
        assertEquals(Result.Success(Unit), task.abort(lease, (failed as Result.Failed).failure))
        assertEquals(
            "FAILED",
            database()
                .queryForObject(
                    "select status from background_jobs where id=?",
                    String::class.java,
                    lease.job.request.id,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from inbox_items where company_id=?",
                    Int::class.java,
                    actor.companyId,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from announcement_publications where company_id=?",
                    Int::class.java,
                    actor.companyId,
                ),
        )
    }
}
