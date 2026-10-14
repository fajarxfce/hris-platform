package dev.fajar.hris.worker

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.data.datasources.SelfProfileProjectionDataSource
import dev.fajar.hris.people.data.repositories.StoredSelfProfileProjectionRepository
import dev.fajar.hris.people.domain.repositories.SelfProfileProjectionRepository
import dev.fajar.hris.people.domain.usecases.RefreshSelfProfiles
import dev.fajar.hris.schema.tables.records.SelfProfileRecordsRecord
import dev.fajar.hris.worker.sync.SelfProfileWorker
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.ObjectMapper

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["hris.worker.enabled=false"],
)
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS
)
class SelfProfileProjectionWorkerTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18.6-alpine").withInitScript("worker-role.sql")

        @DynamicPropertySource
        @JvmStatic
        fun configuration(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { "hris_worker_test" }
            registry.add("spring.datasource.password") { "worker-fixture-only" }
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.flyway.url") { postgres.jdbcUrl }
            registry.add("spring.flyway.user") { postgres.username }
            registry.add("spring.flyway.password") { postgres.password }
        }
    }

    @Autowired private lateinit var source: SelfProfileProjectionDataSource
    @Autowired private lateinit var profiles: SelfProfileProjectionRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var runtime: JdbcTemplate
    @Autowired private lateinit var json: ObjectMapper
    private val now = Instant.parse("2026-10-01T00:00:00Z")

    private fun database() =
        JdbcTemplate(
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        )

    private fun <T> success(result: Result<T>): T {
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    private fun useCase(adapter: SelfProfileProjectionDataSource = source, at: Instant = now) =
        RefreshSelfProfiles(
            StoredSelfProfileProjectionRepository(adapter, json),
            companies,
            transactions,
            Clock.fixed(at, ZoneOffset.UTC),
        )

    private fun actor(company: UUID) =
        Actor(UUID(0, 0), company, emptySet(), now, UUID.randomUUID())

    private data class Fixture(
        val company: UUID,
        val employee: UUID,
        val person: UUID,
        val account: UUID,
    )

    private fun fixture(
        start: String = "2026-01-01",
        end: String? = null,
        company: UUID? = null,
        person: UUID? = null,
        manager: UUID? = null,
        linked: Boolean = true,
    ): Fixture {
        val c = company ?: UUID.randomUUID()
        val p = person ?: UUID.randomUUID()
        val e = UUID.randomUUID()
        val a =
            if (person == null) UUID.randomUUID()
            else
                requireNotNull(
                    database()
                        .queryForObject(
                            "select account_id from persons where id=?",
                            UUID::class.java,
                            person,
                        )
                )
        if (company == null)
            database()
                .update(
                    "insert into companies(id,code,name,timezone) values(?,?,'Profile fixture','Asia/Jakarta')",
                    c,
                    "P${c.toString().take(8)}",
                )
        if (person == null) {
            database()
                .update(
                    "insert into accounts(id,email,display_name) values(?,?,'Profile fixture')",
                    a,
                    "$a@example.test",
                )
            database()
                .update(
                    "insert into persons(id,owner_company_id,account_id,legal_name,birth_date,nationality,email) values(?,?,?,'Employee','1990-02-03','ID','personal@example.test')",
                    p,
                    c,
                    a.takeIf { linked },
                )
        }
        database()
            .update(
                "insert into employments(company_id,id,person_id,employee_number) values(?,?,?,?)",
                c,
                e,
                p,
                "E${e.toString().take(8)}",
            )
        database()
            .update(
                "insert into employment_revisions(company_id,employment_id,revision,effective_from,contract_kind,start_date,end_date,status,manager_id,actor_id,reason) values(?,?,0,?::date,'PERMANENT',?::date,?::date,'ACTIVE',?,?,'Fixture')",
                c,
                e,
                start,
                start,
                end,
                manager,
                a,
            )
        return Fixture(c, e, p, a)
    }

    private fun record(f: Fixture) =
        success(transactions.run(actor(f.company)) { profiles.find(f.company, f.employee) })

    private fun changes(f: Fixture) =
        requireNotNull(
            database()
                .queryForObject(
                    "select count(*) from mobile_sync_changes where company_id=? and collection='SELF_PROFILES' and resource_id=?",
                    Int::class.java,
                    f.company,
                    f.employee,
                )
        )

    private fun due(f: Fixture) =
        database()
            .queryForObject(
                "select refresh_at is not null from self_profile_refresh_queue where company_id=? and employment_id=?",
                Boolean::class.java,
                f.company,
                f.employee,
            )

    @BeforeEach
    fun clearPriorWork() {
        database().update("update self_profile_refresh_queue set refresh_at=null")
    }

    @Test
    fun privateFieldsAndAssignmentReferencesComeFromOneOwnedProjection() {
        val manager = fixture()
        val employee = fixture(company = manager.company, manager = manager.employee)
        assertEquals(2, success(useCase().execute()))
        val value = requireNotNull(record(employee)?.snapshot)
        assertEquals(employee.account, value.details.employee.person.accountId)
        assertEquals(employee.person, value.details.employee.person.id)
        assertEquals("1990-02-03", value.details.employee.person.birthDate.toString())
        assertEquals(manager.employee, value.details.manager?.id)
        assertNull(value.details.employee.managerAccountId)
        val payload =
            requireNotNull(
                database()
                    .queryForObject(
                        "select payload::text from self_profile_records where company_id=? and id=?",
                        String::class.java,
                        employee.company,
                        employee.employee,
                    )
            )
        assertFalse(payload.contains(manager.account.toString()))
        assertFalse(payload.contains(manager.person.toString()))
        assertEquals(1, changes(employee))
        assertEquals(
            0,
            runtime.queryForObject("select count(*) from self_profile_records", Int::class.java),
        )
    }

    @Test
    fun futureAndUnlinkedEmployeesDoNotPublishPrivateFields() {
        val future = fixture(start = "2026-10-02")
        val unlinked = fixture(linked = false)
        assertEquals(2, success(useCase().execute()))
        assertNull(record(future))
        assertNull(record(unlinked))
        assertEquals(0, changes(future))
        assertEquals(0, changes(unlinked))
        assertEquals(1, success(useCase(at = Instant.parse("2026-10-01T17:00:00Z")).execute()))
        assertNotNull(record(future)?.snapshot)
        assertNull(record(unlinked))
    }

    @Test
    fun privateEditsInvalidateSharedPersonsInBothCompaniesWithoutChangingEmploymentVersions() {
        val first = fixture()
        val second = fixture(person = first.person)
        assertEquals(2, success(useCase().execute()))
        database()
            .update(
                "update persons set birth_date='1991-03-04',version=version+1 where id=?",
                first.person,
            )
        assertEquals(2, success(useCase().execute()))
        for (f in listOf(first, second)) {
            val value = requireNotNull(record(f))
            assertEquals(1L, value.version)
            assertEquals(1L, value.snapshot?.profileVersion)
            assertEquals(0L, value.snapshot?.details?.employee?.version)
            assertEquals(first.company, value.snapshot?.ownerCompanyId)
            assertEquals(
                "1991-03-04",
                value.snapshot?.details?.employee?.person?.birthDate.toString(),
            )
        }
        assertEquals(0, success(useCase().execute()))
    }

    @Test
    fun managerNamesInvalidateDependentsWithoutIncludingManagerPrivateFields() {
        val manager = fixture()
        val employee = fixture(company = manager.company, manager = manager.employee)
        success(useCase().execute())
        database()
            .update(
                "update persons set legal_name='New manager',birth_date='1981-12-13',version=version+1 where id=?",
                manager.person,
            )
        assertEquals(2, success(useCase().execute()))
        val value = requireNotNull(record(employee)?.snapshot)
        assertEquals("New manager", value.details.manager?.legalName)
        assertEquals("1990-02-03", value.details.employee.person.birthDate.toString())
        val payload =
            requireNotNull(
                database()
                    .queryForObject(
                        "select payload::text from self_profile_records where company_id=? and id=?",
                        String::class.java,
                        employee.company,
                        employee.employee,
                    )
            )
        assertFalse(payload.contains("1981-12-13"))
    }

    @Test
    fun managerEndAndEffectiveEmploymentChangesAreScheduledInCompanyTime() {
        val manager = fixture(end = "2026-10-01")
        val employee = fixture(company = manager.company, manager = manager.employee)
        success(useCase().execute())
        assertEquals(true, record(employee)?.snapshot?.details?.manager?.working)
        assertEquals(1, success(useCase(at = Instant.parse("2026-10-01T17:00:00Z")).execute()))
        assertEquals(false, record(employee)?.snapshot?.details?.manager?.working)
        assertEquals(1L, record(employee)?.version)
        database()
            .update(
                "insert into employment_revisions(company_id,employment_id,revision,effective_from,contract_kind,start_date,status,manager_id,actor_id,reason) values(?,?,1,'2026-10-03','PERMANENT','2026-01-01','SUSPENDED',?,?,'Scheduled')",
                employee.company,
                employee.employee,
                manager.employee,
                employee.account,
            )
        database()
            .update(
                "update employments set version=1 where company_id=? and id=?",
                employee.company,
                employee.employee,
            )
        success(useCase(at = Instant.parse("2026-10-01T17:00:00Z")).execute())
        assertEquals(0L, record(employee)?.snapshot?.details?.employee?.appliedRevision)
        success(useCase(at = Instant.parse("2026-10-02T17:00:00Z")).execute())
        assertEquals(1L, record(employee)?.snapshot?.details?.employee?.appliedRevision)
        assertEquals(
            "SUSPENDED",
            record(employee)?.snapshot?.details?.employee?.terms?.status?.name,
        )
    }

    @Test
    fun cancellingAFutureRevisionRetainsCurrentFactsAndRemovesItsDueInstant() {
        val f = fixture()
        success(useCase().execute())
        database()
            .update(
                "insert into employment_revisions(company_id,employment_id,revision,effective_from,contract_kind,start_date,status,actor_id,reason) values(?,?,1,'2026-10-02','PERMANENT','2026-01-01','SUSPENDED',?,'Scheduled')",
                f.company,
                f.employee,
                f.account,
            )
        database()
            .update(
                "update employments set version=1 where company_id=? and id=?",
                f.company,
                f.employee,
            )
        success(useCase().execute())
        assertEquals(true, due(f))
        database()
            .update(
                "insert into employment_revision_cancellations(company_id,employment_id,revision,actor_id,reason) values(?,?,1,?,'Cancelled')",
                f.company,
                f.employee,
                f.account,
            )
        database()
            .update(
                "update employments set version=2 where company_id=? and id=?",
                f.company,
                f.employee,
            )
        success(useCase().execute())
        assertEquals(false, due(f))
        assertEquals(0L, record(f)?.snapshot?.details?.employee?.appliedRevision)
        assertEquals(0, success(useCase(at = Instant.parse("2026-10-01T17:00:00Z")).execute()))
    }

    @Test
    fun assignmentLabelsAndArchiveStatusProduceOneNewRecord() {
        val f = fixture()
        val unit = UUID.randomUUID()
        database()
            .update(
                "insert into organization_units(company_id,id,kind,code,name) values(?,?,'DEPARTMENT','ENG','Engineering')",
                f.company,
                unit,
            )
        database()
            .update(
                "insert into employment_revisions(company_id,employment_id,revision,effective_from,contract_kind,start_date,status,department_id,actor_id,reason) values(?,?,1,'2026-02-01','PERMANENT','2026-01-01','ACTIVE',?,?,'Assignment')",
                f.company,
                f.employee,
                unit,
                f.account,
            )
        database()
            .update(
                "update employments set version=1 where company_id=? and id=?",
                f.company,
                f.employee,
            )
        success(useCase().execute())
        assertEquals(unit, record(f)?.snapshot?.details?.department?.id)
        database()
            .update(
                "update organization_units set name='Product',active=false,version=version+1 where company_id=? and id=?",
                f.company,
                unit,
            )
        success(useCase().execute())
        assertEquals(1L, record(f)?.version)
        assertEquals("Product", record(f)?.snapshot?.details?.department?.name)
        assertEquals(false, record(f)?.snapshot?.details?.department?.active)
        database()
            .update(
                "update organization_units set name=name,version=version+1 where company_id=? and id=?",
                f.company,
                unit,
            )
        success(useCase().execute())
        assertEquals(1L, record(f)?.version)
        assertEquals(2, changes(f))
    }

    @Test
    fun changingTimezoneCanWithdrawAndRepublishAnEffectiveProfile() {
        val f = fixture(start = "2026-10-01")
        success(useCase().execute())
        database()
            .update(
                "update companies set timezone='America/New_York',version=version+1 where id=?",
                f.company,
            )
        success(useCase().execute())
        assertNull(record(f)?.snapshot)
        assertEquals(1L, record(f)?.version)
        success(useCase(at = Instant.parse("2026-10-01T04:00:00Z")).execute())
        assertEquals("America/New_York", record(f)?.snapshot?.timezone)
        assertEquals(2L, record(f)?.version)
        assertEquals(
            1,
            database()
                .queryForObject(
                    "select count(*) from mobile_sync_changes where company_id=? and collection='SELF_PROFILES' and operation='DELETE'",
                    Int::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun concurrentWorkersSkipLockedRowsWithoutDuplicatePublication() {
        val first = fixture()
        val second = fixture(company = first.company)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val adapter =
            object : SelfProfileProjectionDataSource by source {
                override fun lockDue(company: UUID, now: Instant, limit: Int) =
                    source.lockDue(company, now, 1)

                override fun candidates(
                    company: UUID,
                    ids: List<UUID>,
                    today: java.time.LocalDate,
                ): List<dev.fajar.hris.people.data.models.SelfProfileCandidateRow> {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return source.candidates(company, ids, today)
                }
            }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val pending = pool.submit<Result<Int>> { useCase(adapter).execute() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(
                1,
                success(pool.submit<Result<Int>> { useCase().execute() }.get(5, TimeUnit.SECONDS)),
            )
            release.countDown()
            assertEquals(1, success(pending.get(5, TimeUnit.SECONDS)))
            assertEquals(1, changes(first))
            assertEquals(1, changes(second))
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun aSourceWriteWaitingOnProjectionCompletionStillSchedulesItsNewFacts() {
        val f = fixture()
        val acquired = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writing = CountDownLatch(1)
        val adapter =
            object : SelfProfileProjectionDataSource by source {
                override fun candidates(
                    company: UUID,
                    ids: List<UUID>,
                    today: java.time.LocalDate,
                ): List<dev.fajar.hris.people.data.models.SelfProfileCandidateRow> {
                    val rows = source.candidates(company, ids, today)
                    acquired.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return rows
                }
            }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val pending = pool.submit<Result<Int>> { useCase(adapter).execute() }
            assertTrue(acquired.await(5, TimeUnit.SECONDS))
            val writer =
                pool.submit {
                    writing.countDown()
                    database()
                        .update(
                            "/* profile-late-source */ update persons set legal_name='Changed while projecting',version=version+1 where id=?",
                            f.person,
                        )
                }
            assertTrue(writing.await(5, TimeUnit.SECONDS))
            val blocked =
                (1..200).any {
                    val waiting =
                        requireNotNull(
                            database()
                                .queryForObject(
                                    "select count(*) from pg_stat_activity where wait_event_type='Lock' and query like '/* profile-late-source */%'",
                                    Int::class.java,
                                )
                        ) > 0
                    if (!waiting) Thread.sleep(10)
                    waiting
                }
            assertTrue(blocked, "The source must wait for the worker's queue row")
            release.countDown()
            success(pending.get(5, TimeUnit.SECONDS))
            writer.get(5, TimeUnit.SECONDS)
            assertEquals(true, due(f))
            success(useCase().execute())
            assertEquals(
                "Changed while projecting",
                record(f)?.snapshot?.details?.employee?.person?.legalName,
            )
            assertEquals(1L, record(f)?.version)
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun stoppingTheWorkerRollsBackItsRecordAndKeepsDueWorkRecoverable() {
        val f = fixture()
        val written = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val adapter =
            object : SelfProfileProjectionDataSource by source {
                override fun save(record: SelfProfileRecordsRecord) {
                    source.save(record)
                    written.countDown()
                    try {
                        CountDownLatch(1).await(15, TimeUnit.SECONDS)
                    } catch (error: InterruptedException) {
                        interrupted.countDown()
                        throw error
                    }
                }
            }
        val timer =
            ThreadPoolTaskScheduler().apply {
                poolSize = 1
                setRemoveOnCancelPolicy(true)
                setWaitForTasksToCompleteOnShutdown(false)
                initialize()
            }
        val worker = SelfProfileWorker(useCase(adapter), timer, Duration.ofMillis(20))
        try {
            worker.start()
            assertTrue(written.await(5, TimeUnit.SECONDS))
            worker.stop()
            assertTrue(interrupted.await(5, TimeUnit.SECONDS))
            assertTrue(timer.scheduledThreadPoolExecutor.awaitTermination(5, TimeUnit.SECONDS))
            assertNull(record(f))
            assertEquals(true, due(f))
            assertEquals(0, changes(f))
            assertFalse(worker.isRunning)
            assertTrue(timer.scheduledThreadPoolExecutor.queue.isEmpty())
            assertThrows(IllegalStateException::class.java) { worker.start() }
        } finally {
            worker.stop()
        }
        assertEquals(1, success(useCase().execute()))
        assertNotNull(record(f)?.snapshot)
    }

    @Test
    fun captureAndRuntimeRolesCannotReadAnotherCompanyOrPrivatePersonFields() {
        val f = fixture()
        success(useCase().execute())
        assertEquals(
            false,
            runtime.queryForObject(
                "select pg_has_role(current_user,'hris_profile_capture','MEMBER')",
                Boolean::class.java,
            ),
        )
        assertEquals(
            false,
            database()
                .queryForObject(
                    "select rolcanlogin or rolsuper or rolbypassrls from pg_roles where rolname='hris_profile_capture'",
                    Boolean::class.java,
                ),
        )
        assertEquals(
            false,
            database()
                .queryForObject(
                    "select has_table_privilege('hris_profile_capture','persons','SELECT')",
                    Boolean::class.java,
                ),
        )
        assertEquals(
            false,
            database()
                .queryForObject(
                    "select has_column_privilege('hris_profile_capture','employment_revisions','reason','SELECT')",
                    Boolean::class.java,
                ),
        )
        assertEquals(
            true,
            database()
                .queryForObject(
                    "select has_column_privilege('hris_profile_capture','employment_revisions','manager_id','SELECT')",
                    Boolean::class.java,
                ),
        )
        assertNull(
            success(
                transactions.run(actor(UUID.randomUUID())) { profiles.find(f.company, f.employee) }
            )
        )
    }

    @Test
    fun eachPassBoundsCompanyDiscoveryAndResumesRemainingRows() {
        repeat(5) { fixture() }
        assertEquals(4, success(useCase().execute()))
        assertEquals(1, success(useCase().execute()))
        val first = fixture()
        repeat(204) { fixture(company = first.company) }
        assertEquals(200, success(useCase().execute()))
        assertEquals(5, success(useCase().execute()))
        assertEquals(0, success(useCase().execute()))
    }
}
