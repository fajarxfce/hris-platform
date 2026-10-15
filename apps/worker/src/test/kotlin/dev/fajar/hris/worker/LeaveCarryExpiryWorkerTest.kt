package dev.fajar.hris.worker

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.usecases.LeaseJobs
import dev.fajar.hris.leave.domain.entities.*
import dev.fajar.hris.leave.domain.repositories.*
import dev.fajar.hris.leave.domain.usecases.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.usecases.CreateEmployee
import dev.fajar.hris.worker.runtime.BatchJobExecutor
import dev.fajar.hris.worker.tasks.LeaveCarryExpiryTask
import dev.fajar.hris.worker.tasks.LeaveYearCloseTask
import java.time.*
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
@Import(CarryExpiryWorkerClockConfiguration::class)
class LeaveCarryExpiryWorkerTest {
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

    @Autowired private lateinit var clock: CarryExpiryWorkerClock
    @Autowired private lateinit var employees: CreateEmployee
    @Autowired private lateinit var savePolicy: SaveLeaveType
    @Autowired private lateinit var adjust: AdjustLeaveBalance
    @Autowired private lateinit var start: StartLeaveYearCloseBatch
    @Autowired private lateinit var resumeClosing: ResumeLeaveBatch
    @Autowired private lateinit var resumeExpiry: ResumeLeaveCarryExpiry
    @Autowired private lateinit var leases: LeaseJobs
    @Autowired private lateinit var closingTask: LeaveYearCloseTask
    @Autowired private lateinit var expiryTask: LeaveCarryExpiryTask
    @Autowired private lateinit var executor: BatchJobExecutor
    @Autowired private lateinit var expiries: LeaveCarryExpiryRepository
    @Autowired private lateinit var transactions: TransactionRunner
    private val companies = mutableSetOf<UUID>()

    private data class Fixture(val actor: Actor, val type: UUID, val batch: UUID)

    private fun database() =
        JdbcTemplate(
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        )

    @AfterEach
    fun release() {
        for (company in companies) database()
            .update(
                """update background_jobs set status='CANCELLED',finished_at=now(),
            lease_owner=null,lease_token=null,lease_until=null where company_id=? and status in ('QUEUED','RUNNING')""",
                company,
            )
    }

    private fun <T> success(result: Result<T>): T {
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    private fun operator(company: UUID): Actor {
        val id = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,mfa_secret_encrypted) values(?,?,'Expiry operator','fixture-enrolled')",
                id,
                "$id@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                id,
            )
        for (permission in PermissionCatalog.companyAdministrator) database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,?)",
                company,
                id,
                permission,
            )
        return Actor(
            id,
            company,
            PermissionCatalog.companyAdministrator,
            clock.instant(),
            UUID.randomUUID(),
            mfaVerifiedAt = clock.instant(),
            credentialVersion = 0,
        )
    }

    private fun fixture(): Fixture {
        // Admission precedes its deadline; the deadline is already due on the real database clock.
        val year = LocalDate.now(ZoneOffset.UTC).year - 1
        clock.set(LocalDate.of(year, 1, 5).atStartOfDay(ZoneOffset.UTC).toInstant())
        val company = UUID.randomUUID()
        companies += company
        database()
            .update(
                "insert into companies(id,code,name,timezone) values(?,?,?,'UTC')",
                company,
                "C${company.toString().take(8)}",
                "Carry expiry fixture",
            )
        val actor = operator(company)
        val type = UUID.randomUUID()
        val from = LocalDate.of(year - 2, 1, 1)
        success(
            savePolicy.execute(
                actor,
                UUID.randomUUID(),
                LeaveType(
                    type,
                    "ANNUAL",
                    from,
                    LeavePolicy(
                        "Annual leave",
                        true,
                        true,
                        0,
                        setOf(ContractKind.PERMANENT),
                        30,
                        accrual = LeaveAccrualPolicy(LeaveAccrualFrequency.MANUAL, 0, 4, 3),
                    ),
                    true,
                    0,
                    0,
                ),
                null,
                "Expiry fixture policy",
            )
        )
        for (number in 1..2) {
            val employee = UUID.randomUUID()
            success(
                employees.execute(
                    actor,
                    UUID.randomUUID(),
                    employee,
                    "E$number",
                    PersonProfile(UUID.randomUUID(), null, "Employee $number", null, "ID", null),
                    EmploymentTerms(
                        from,
                        ContractKind.PERMANENT,
                        from,
                        null,
                        EmploymentStatus.ACTIVE,
                        null,
                        null,
                        null,
                        null,
                        null,
                    ),
                    "Expiry fixture employment",
                )
            )
            success(
                adjust.execute(
                    actor,
                    UUID.randomUUID(),
                    employee,
                    type,
                    year - 1,
                    4,
                    "Reviewed entitlement",
                    0,
                )
            )
        }
        val batch = UUID.randomUUID()
        success(
            start.execute(
                actor,
                UUID.randomUUID(),
                batch,
                type,
                year - 1,
                0,
                null,
                "Reviewed year closing",
            )
        )
        return Fixture(actor, type, batch)
    }

    private fun claim(kind: JobKind): JobLease =
        success(leases.execute(UUID.randomUUID(), 1, 120, setOf(kind))).single()

    private fun origin(f: Fixture): LeaveCarryExpiry =
        success(
                transactions.run(f.actor) {
                    expiries.list(requireNotNull(f.actor.companyId), null, 10)
                }
            )
            .items
            .single()

    private fun outcomes(f: Fixture): List<LeaveCarryExpiryOutcome> {
        val id = origin(f).id
        return success(
                transactions.run(f.actor) {
                    expiries.results(requireNotNull(f.actor.companyId), id, null, 10)
                }
            )
            .items
    }

    @Test
    fun restrictedWorkerSettlesTheOriginalCohortAfterItsCreatorLosesAccess() {
        val f = fixture()
        success(executor.execute(claim(JobKind.LEAVE_YEAR_CLOSE), closingTask))
        val original = origin(f)
        database()
            .update(
                "update company_memberships set active=false where company_id=? and account_id=?",
                f.actor.companyId,
                f.actor.accountId,
            )
        clock.set(Instant.now())
        success(executor.execute(claim(JobKind.LEAVE_CARRY_EXPIRY), expiryTask))
        assertEquals(original, origin(f))
        assertEquals(listOf(4, 4), outcomes(f).map { it.halfDays })
        assertTrue(outcomes(f).all { it.status == LeaveCarryExpiryOutcomeStatus.EXPIRED })
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select sum(available_half_days)::integer from leave_carry_buckets where company_id=?",
                    Int::class.java,
                    f.actor.companyId,
                ),
        )
        assertEquals(
            "SUCCEEDED",
            database()
                .queryForObject(
                    "select status from background_jobs where id=?",
                    String::class.java,
                    original.jobId,
                ),
        )
    }

    @Test
    fun anotherAuthorizedOperatorRecoversOnlyTheUnfinishedPrefix() {
        val f = fixture()
        success(executor.execute(claim(JobKind.LEAVE_YEAR_CLOSE), closingTask))
        clock.set(Instant.now())
        val lease = claim(JobKind.LEAVE_CARRY_EXPIRY)
        assertEquals(JobStep(1, false), success(expiryTask.advance(lease)))
        success(
            expiryTask.abort(lease, Failure(FailureKind.CONFLICT, "fixture_processing_stopped"))
        )
        val original = origin(f)
        val first = outcomes(f).single()
        database()
            .update(
                "update company_memberships set active=false where company_id=? and account_id=?",
                f.actor.companyId,
                f.actor.accountId,
            )
        val revoked =
            f.actor.copy(authenticatedAt = clock.instant(), mfaVerifiedAt = clock.instant())
        assertTrue(
            resumeExpiry.execute(revoked, UUID.randomUUID(), original.id, 0, "Resume fixed expiry")
                is Result.Failed
        )
        assertEquals(original, origin(f))
        val replacement = operator(requireNotNull(f.actor.companyId))
        val operation = UUID.randomUUID()
        success(resumeExpiry.execute(replacement, operation, original.id, 0, "Resume fixed expiry"))
        val resumed = origin(f)
        assertEquals(original.copy(jobId = resumed.jobId, version = 1), resumed)
        assertTrue(
            success(
                    resumeExpiry.execute(
                        replacement,
                        operation,
                        original.id,
                        0,
                        "Resume fixed expiry",
                    )
                )
                .replayed
        )
        assertEquals("job_lease_lost", (expiryTask.advance(lease) as Result.Failed).failure.code)
        success(executor.execute(claim(JobKind.LEAVE_CARRY_EXPIRY), expiryTask))
        assertEquals(first, outcomes(f).first())
        assertEquals(2, outcomes(f).size)
        assertEquals(8, outcomes(f).sumOf { it.halfDays })
        val attempts =
            success(
                transactions.run(replacement) {
                    expiries.attempts(requireNotNull(replacement.companyId), original.id)
                }
            )
        assertEquals(listOf(0, 1), attempts.map { it.baseCompleted })
        assertEquals(
            2,
            database()
                .queryForObject(
                    "select count(*) from leave_ledger where company_id=? and carry_expiry_id is not null",
                    Int::class.java,
                    f.actor.companyId,
                ),
        )
    }

    @Test
    fun aClosingResumedAfterExpiryCannotIntroduceCreditForAnAlreadyProcessedTarget() {
        val f = fixture()
        val closingLease = claim(JobKind.LEAVE_YEAR_CLOSE)
        success(closingTask.advance(closingLease))
        success(
            closingTask.abort(
                closingLease,
                Failure(FailureKind.CONFLICT, "fixture_processing_stopped"),
            )
        )
        clock.set(Instant.now())
        success(executor.execute(claim(JobKind.LEAVE_CARRY_EXPIRY), expiryTask))
        assertEquals(
            setOf(LeaveCarryExpiryOutcomeStatus.EXPIRED, LeaveCarryExpiryOutcomeStatus.NO_AWARD),
            outcomes(f).map { it.status }.toSet(),
        )
        val before = outcomes(f)
        val version =
            database()
                .queryForObject(
                    "select version from leave_batches where company_id=? and id=?",
                    Long::class.java,
                    f.actor.companyId,
                    f.batch,
                )!!
        success(
            resumeClosing.execute(
                f.actor.copy(authenticatedAt = clock.instant(), mfaVerifiedAt = clock.instant()),
                UUID.randomUUID(),
                f.batch,
                version,
                "Complete original year closing",
            )
        )
        success(executor.execute(claim(JobKind.LEAVE_YEAR_CLOSE), closingTask))
        assertEquals(before, outcomes(f))
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from leave_carry_buckets where company_id=?",
                    Int::class.java,
                    f.actor.companyId,
                ),
        )
        assertEquals(
            0,
            database()
                .queryForObject(
                    "select sum(available_half_days)::integer from leave_carry_buckets where company_id=?",
                    Int::class.java,
                    f.actor.companyId,
                ),
        )
    }
}

class CarryExpiryWorkerClock
private constructor(private val value: AtomicReference<Instant>, private val timezone: ZoneId) :
    Clock() {
    constructor() : this(AtomicReference(Instant.now()), ZoneOffset.UTC)

    fun set(instant: Instant) {
        value.set(instant)
    }

    override fun instant(): Instant = value.get()

    override fun getZone(): ZoneId = timezone

    override fun withZone(zone: ZoneId): Clock = CarryExpiryWorkerClock(value, zone)
}

@TestConfiguration(proxyBeanMethods = false)
class CarryExpiryWorkerClockConfiguration {
    @Bean @Primary fun carryExpiryWorkerClock() = CarryExpiryWorkerClock()
}
