package dev.fajar.hris.worker

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.usecases.LeaseJobs
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.usecases.*
import dev.fajar.hris.worker.runtime.BatchJobExecutor
import dev.fajar.hris.worker.tasks.OnboardingScheduleTask
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.annotation.DirtiesContext
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
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(OnboardingWorkerClockConfiguration::class)
class OnboardingScheduleWorkerTest {
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
            registry.add("HRIS_IDENTITY_KEYS") {
                "v1:" + java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 23 })
            }
        }
    }

    @Autowired private lateinit var clock: OnboardingWorkerClock
    @Autowired private lateinit var employees: CreateEmployee
    @Autowired private lateinit var templates: SaveLifecycleTemplate
    @Autowired private lateinit var review: GetOnboardingScheduleReview
    @Autowired private lateinit var schedule: ScheduleOnboarding
    @Autowired private lateinit var cancel: CancelOnboardingSchedule
    @Autowired private lateinit var leases: LeaseJobs
    @Autowired private lateinit var task: OnboardingScheduleTask
    @Autowired private lateinit var executor: BatchJobExecutor
    private val companies = mutableSetOf<UUID>()

    private data class Fixture(
        val actor: Actor,
        val employee: UUID,
        val caseId: UUID,
        val scheduleId: UUID,
        val account: UUID,
    )

    private fun database() =
        JdbcTemplate(
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        )

    @AfterEach
    fun stopOwnedJobs() {
        for (company in companies) database()
            .update(
                "update background_jobs set status='CANCELLED',finished_at=now(),lease_owner=null,lease_token=null,lease_until=null where company_id=? and status in ('QUEUED','RUNNING')",
                company,
            )
    }

    @Test
    fun springBatchCompletesAnAdmittedScheduleAfterInteractiveMfaHasExpired() {
        val f = fixture()
        clock.set(Instant.now())
        val lease = claim()
        assertEquals(Result.Success(Unit), executor.execute(lease, task))
        assertEquals(
            "SUCCEEDED",
            database()
                .queryForObject(
                    "select status from background_jobs where id=?",
                    String::class.java,
                    lease.job.request.id,
                ),
        )
        assertEquals(
            "STARTED",
            database()
                .queryForObject(
                    "select status from onboarding_schedules where company_id=? and id=?",
                    String::class.java,
                    f.actor.companyId,
                    f.scheduleId,
                ),
        )
        assertEquals(
            "ACTIVE",
            database()
                .queryForObject(
                    "select status from employment_revisions where company_id=? and employment_id=? order by revision desc limit 1",
                    String::class.java,
                    f.actor.companyId,
                    f.employee,
                ),
        )
        assertTrue(
            database()
                .queryForObject(
                    "select active from company_memberships where company_id=? and account_id=?",
                    Boolean::class.java,
                    f.actor.companyId,
                    f.account,
                )!!
        )
        assertTrue(
            database()
                .queryForObject(
                    "select active from accounts where id=?",
                    Boolean::class.java,
                    f.account,
                )!!
        )
        assertEquals(
            "OPEN",
            database()
                .queryForObject(
                    "select status from lifecycle_cases where company_id=? and id=?",
                    String::class.java,
                    f.actor.companyId,
                    f.caseId,
                ),
        )
        assertEquals(
            "PENDING",
            database()
                .queryForObject(
                    "select status from lifecycle_tasks where company_id=? and case_id=?",
                    String::class.java,
                    f.actor.companyId,
                    f.caseId,
                ),
        )
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from employment_revisions where company_id=?",
                    Int::class.java,
                    f.actor.companyId,
                ),
        )
        assertEquals("job_lease_lost", (task.advance(lease) as Result.Failed).failure.code)
    }

    @Test
    fun revokedCredentialsCannotExecuteButScopedCleanupStillClosesTheSchedule() {
        val f = fixture()
        clock.set(Instant.now())
        val lease = claim()
        database()
            .update(
                "update accounts set security_version=security_version+1 where id=?",
                f.actor.accountId,
            )
        val failed = executor.execute(lease, task) as Result.Failed
        assertEquals("session_revoked", failed.failure.code)
        assertEquals(Result.Success(Unit), task.abort(lease, failed.failure))
        assertEquals(
            "FAILED",
            database()
                .queryForObject(
                    "select status from onboarding_schedules where company_id=? and id=?",
                    String::class.java,
                    f.actor.companyId,
                    f.scheduleId,
                ),
        )
        assertEquals(
            "ACTIVE",
            database()
                .queryForObject(
                    "select status from employment_revisions where company_id=? and employment_id=? order by revision desc limit 1",
                    String::class.java,
                    f.actor.companyId,
                    f.employee,
                ),
        )
    }

    @Test
    fun cancellingBeforeTheExecutionDateWakesAndStopsTheFutureJob() {
        val f = fixture(future = true)
        val job =
            database()
                .queryForMap(
                    "select id,version from background_jobs where company_id=? and kind='PEOPLE_ONBOARD_START'",
                    f.actor.companyId,
                )
        assertTrue(
            cancel.execute(
                f.actor,
                UUID.randomUUID(),
                f.employee,
                f.scheduleId,
                0,
                (job["version"] as Number).toLong(),
                "Preparation withdrawn",
            ) is Result.Success
        )
        val lease = claim()
        val failed = executor.execute(lease, task) as Result.Failed
        assertEquals("job_cancellation_requested", failed.failure.code)
        assertEquals(Result.Success(Unit), task.abort(lease, failed.failure))
        assertEquals(
            "CANCELLED",
            database()
                .queryForObject(
                    "select status from onboarding_schedules where company_id=? and id=?",
                    String::class.java,
                    f.actor.companyId,
                    f.scheduleId,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select count(*) from lifecycle_cases where company_id=?",
                    Int::class.java,
                    f.actor.companyId,
                ),
        )
        assertTrue(
            database()
                .queryForObject(
                    "select active from company_memberships where company_id=? and account_id=?",
                    Boolean::class.java,
                    f.actor.companyId,
                    f.account,
                )!!
        )
    }

    private fun claim(): JobLease {
        val leased = leases.execute(UUID.randomUUID(), 1, 120, setOf(JobKind.PEOPLE_ONBOARD_START))
        assertTrue(leased is Result.Success, leased.toString())
        return (leased as Result.Success).value.single()
    }

    private fun fixture(future: Boolean = false): Fixture {
        val at =
            Instant.now()
                .truncatedTo(ChronoUnit.MICROS)
                .minus(if (future) Duration.ZERO else Duration.ofDays(2))
        clock.set(at)
        val company = UUID.randomUUID()
        val operator = UUID.randomUUID()
        val target = UUID.randomUUID()
        companies += company
        val db = database()
        db.update(
            "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
            company,
            "O${company.toString().take(8)}",
            "Onboarding worker fixture",
        )
        for (account in listOf(operator, target)) {
            db.update(
                "insert into accounts(id,email,display_name,mfa_secret_encrypted) values(?,?,'Onboarding fixture','fixture-enrolled')",
                account,
                "$account@example.test",
            )
            db.update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                account,
            )
        }
        for (permission in PermissionCatalog.companyAdministrator) db.update(
            "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
            company,
            operator,
            permission,
        )
        db.update(
            "insert into membership_permissions(company_id,account_id,permission) values(?,?,'people.lifecycle.perform')",
            company,
            target,
        )
        val actor =
            Actor(
                operator,
                company,
                PermissionCatalog.companyAdministrator,
                at,
                UUID.randomUUID(),
                mfaVerifiedAt = at,
                credentialVersion = 0,
            )
        val employee = UUID.randomUUID()
        val day = at.atZone(ZoneOffset.UTC).toLocalDate()
        val joined = day.plusDays(3)
        val created =
            employees.execute(
                actor,
                UUID.randomUUID(),
                employee,
                "ONBOARD001",
                PersonProfile(UUID.randomUUID(), target, "Incoming employee", null, "ID", null),
                EmploymentTerms(
                    joined,
                    ContractKind.PERMANENT,
                    joined,
                    null,
                    EmploymentStatus.ACTIVE,
                    null,
                    null,
                    null,
                    null,
                    null,
                ),
                "Worker onboarding fixture",
            )
        assertTrue(created is Result.Success, created.toString())
        val template =
            LifecycleTemplate(
                UUID.randomUUID(),
                "JOIN",
                "Joining checklist",
                LifecycleKind.ONBOARDING,
                true,
                0,
                listOf(LifecycleTaskDefinition("equipment", "Equipment returned", true, 0)),
            )
        assertTrue(
            templates.execute(actor, UUID.randomUUID(), template, null, "Worker checklist")
                is Result.Success
        )
        val caseId = UUID.randomUUID()
        val selection = OnboardingScheduleSelection(template.id, joined, day.plusDays(1))
        val prepared = review.execute(actor, employee, selection)
        assertTrue(prepared is Result.Success, prepared.toString())
        val scheduleId = UUID.randomUUID()
        val admitted =
            schedule.execute(
                actor,
                UUID.randomUUID(),
                ScheduleOnboardingCommand(
                    scheduleId,
                    caseId,
                    employee,
                    selection,
                    0,
                    0,
                    (prepared as Result.Success).value.basisRevision,
                    mapOf("equipment" to target),
                    "Reviewed onboarding",
                ),
            )
        assertTrue(admitted is Result.Success, admitted.toString())
        return Fixture(actor, employee, caseId, scheduleId, target)
    }
}

class OnboardingWorkerClock
private constructor(private val value: AtomicReference<Instant>, private val timezone: ZoneId) :
    Clock() {
    constructor() : this(AtomicReference(Instant.now()), ZoneOffset.UTC)

    fun set(instant: Instant) {
        value.set(instant)
    }

    override fun instant(): Instant = value.get()

    override fun getZone(): ZoneId = timezone

    override fun withZone(zone: ZoneId): Clock = OnboardingWorkerClock(value, zone)
}

@TestConfiguration(proxyBeanMethods = false)
class OnboardingWorkerClockConfiguration {
    @Bean @Primary fun onboardingWorkerClock() = OnboardingWorkerClock()
}
