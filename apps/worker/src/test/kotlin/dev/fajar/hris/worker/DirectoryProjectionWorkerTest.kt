package dev.fajar.hris.worker

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import dev.fajar.hris.people.data.datasources.DirectoryProjectionDataSource
import dev.fajar.hris.people.data.repositories.StoredDirectoryProjectionRepository
import dev.fajar.hris.people.domain.repositories.DirectoryProjectionRepository
import dev.fajar.hris.people.domain.usecases.RefreshCompanyDirectory
import dev.fajar.hris.worker.sync.CompanyDirectoryWorker
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

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["hris.worker.enabled=false"],
)
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS
)
class DirectoryProjectionWorkerTest {
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

    @Autowired private lateinit var source: DirectoryProjectionDataSource
    @Autowired private lateinit var projections: DirectoryProjectionRepository
    @Autowired private lateinit var companies: CompanyRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var runtime: JdbcTemplate
    private val now = Instant.parse("2026-10-01T00:00:00Z")

    private fun database() =
        JdbcTemplate(
            DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        )

    private fun <T> success(result: Result<T>): T {
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    private fun useCase(adapter: DirectoryProjectionDataSource = source, at: Instant = now) =
        RefreshCompanyDirectory(
            StoredDirectoryProjectionRepository(adapter),
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
        status: String = "ACTIVE",
        company: UUID? = null,
        person: UUID? = null,
    ): Fixture {
        val c = company ?: UUID.randomUUID()
        val p = person ?: UUID.randomUUID()
        val e = UUID.randomUUID()
        val a = UUID.randomUUID()
        if (company == null)
            database()
                .update(
                    "insert into companies(id,code,name,timezone) values(?,?,'Directory fixture','Asia/Jakarta')",
                    c,
                    "D${c.toString().take(8)}",
                )
        database()
            .update(
                "insert into accounts(id,email,display_name) values(?,?,'Projection fixture')",
                a,
                "$a@example.test",
            )
        if (person == null)
            database()
                .update(
                    "insert into persons(id,owner_company_id,legal_name,nationality) values(?,?,'Coworker','ID')",
                    p,
                    c,
                )
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
                "insert into employment_revisions(company_id,employment_id,revision,effective_from,contract_kind,start_date,end_date,status,actor_id,reason) values(?,?,0,?::date,'PERMANENT',?::date,?::date,?,?,'Fixture')",
                c,
                e,
                start,
                start,
                end,
                status,
                a,
            )
        return Fixture(c, e, p, a)
    }

    private fun record(f: Fixture) =
        success(transactions.run(actor(f.company)) { projections.find(f.company, f.employee) })

    private fun changes(f: Fixture) =
        database()
            .queryForObject(
                "select count(*) from mobile_sync_changes where company_id=? and collection='DIRECTORY_EMPLOYEES' and resource_id=?",
                Int::class.java,
                f.company,
                f.employee,
            )!!

    @BeforeEach
    fun clearPriorDueWork() {
        database().update("update directory_refresh_queue set refresh_at=null")
    }

    @Test
    fun currentCoworkersPublishWhileFutureAndSuspendedEmployeesRemainAbsent() {
        val current = fixture()
        val future = fixture(start = "2026-10-02", company = current.company)
        val suspended = fixture(status = "SUSPENDED", company = current.company)
        assertEquals(3, success(useCase().execute()))
        assertEquals("Coworker", record(current)?.employee?.name)
        assertEquals(0L, record(current)?.version)
        assertNull(record(future))
        assertNull(record(suspended))
        assertEquals(0, changes(future))
        assertEquals(0, success(useCase().execute()))
        assertEquals(1, success(useCase(at = Instant.parse("2026-10-01T17:00:00Z")).execute()))
        assertNotNull(record(future)?.employee)
    }

    @Test
    fun anEndDatePublishesATombstoneAndRestoredEmploymentKeepsItsIdentity() {
        val f = fixture(end = "2026-10-01")
        success(useCase().execute())
        success(useCase(at = Instant.parse("2026-10-01T17:00:00Z")).execute())
        assertNull(record(f)?.employee)
        assertEquals(1L, record(f)?.version)
        database()
            .update(
                "insert into employment_revisions(company_id,employment_id,revision,effective_from,contract_kind,start_date,status,actor_id,reason) values(?,?,1,'2026-10-02','PERMANENT','2026-01-01','ACTIVE',?,'Return')",
                f.company,
                f.employee,
                f.account,
            )
        success(useCase(at = Instant.parse("2026-10-01T17:00:00Z")).execute())
        assertEquals(2L, record(f)?.version)
        assertNotNull(record(f)?.employee)
        assertEquals(
            listOf("UPSERT", "DELETE", "UPSERT"),
            database()
                .queryForList(
                    "select operation from mobile_sync_changes where company_id=? and collection='DIRECTORY_EMPLOYEES' order by resource_version",
                    String::class.java,
                    f.company,
                ),
        )
    }

    @Test
    fun aCancelledFutureRevisionDoesNotRemoveTheCoworkerAndUnchangedContentDoesNotAdvanceVersion() {
        val f = fixture()
        success(useCase().execute())
        database()
            .update(
                "insert into employment_revisions(company_id,employment_id,revision,effective_from,contract_kind,start_date,status,actor_id,reason) values(?,?,1,'2026-10-02','PERMANENT','2026-01-01','SUSPENDED',?,'Scheduled')",
                f.company,
                f.employee,
                f.account,
            )
        success(useCase().execute())
        assertEquals(0L, record(f)?.version)
        assertEquals(1, changes(f))
        database()
            .update(
                "insert into employment_revision_cancellations(company_id,employment_id,revision,actor_id,reason) values(?,?,1,?,'Cancelled')",
                f.company,
                f.employee,
                f.account,
            )
        success(useCase().execute())
        assertEquals(0, success(useCase(at = Instant.parse("2026-10-01T17:00:00Z")).execute()))
        assertNotNull(record(f)?.employee)
    }

    @Test
    fun aNameChangeReachesEveryCompanyButPrivateProfileChangesDoNotCreateDirectoryEvents() {
        val first = fixture()
        val second = fixture(person = first.person)
        success(useCase().execute())
        success(
            transactions.run(actor(first.company)) {
                runtime.update(
                    "update persons set birth_date='2000-01-01',version=version+1 where id=?",
                    first.person,
                )
                Result.Success(Unit)
            }
        )
        assertEquals(0, success(useCase().execute()))
        success(
            transactions.run(actor(first.company)) {
                runtime.update(
                    "update persons set legal_name='Updated coworker',version=version+1 where id=?",
                    first.person,
                )
                Result.Success(Unit)
            }
        )
        assertEquals(2, success(useCase().execute()))
        assertEquals("Updated coworker", record(first)?.employee?.name)
        assertEquals("Updated coworker", record(second)?.employee?.name)
        assertEquals(1L, record(second)?.version)
        assertNull(
            success(
                transactions.run(actor(first.company)) {
                    projections.find(second.company, second.employee)
                }
            )
        )
    }

    @Test
    fun assignmentAndLabelChangesInvalidateOnlyTheirPublicRecords() {
        val f = fixture()
        val unrelated = fixture(company = f.company)
        val dept = UUID.randomUUID()
        database()
            .update(
                "insert into organization_units(company_id,id,code,name,kind) values(?,?,'ENG','Engineering','DEPARTMENT')",
                f.company,
                dept,
            )
        database()
            .update(
                "insert into employment_revisions(company_id,employment_id,revision,effective_from,contract_kind,start_date,status,department_id,actor_id,reason) values(?,?,1,'2026-09-01','PERMANENT','2026-01-01','ACTIVE',?,?,'Assignment')",
                f.company,
                f.employee,
                dept,
                f.account,
            )
        success(useCase().execute())
        assertEquals(dept, record(f)?.employee?.departmentId)
        database()
            .update(
                "update organization_units set name='Product engineering',version=version+1 where company_id=? and id=?",
                f.company,
                dept,
            )
        assertEquals(1, success(useCase().execute()))
        assertEquals("Product engineering", record(f)?.employee?.department)
        assertEquals(1L, record(f)?.version)
        assertEquals(0L, record(unrelated)?.version)
    }

    @Test
    fun crossCompanyCaptureHasOnlyIdentityAndQueuePrivilegesWithoutABusinessRlsBypass() {
        val db = database()
        assertEquals(
            "hris_directory_capture",
            db.queryForObject(
                "select pg_get_userbyid(proowner) from pg_proc where proname='capture_directory_person_refresh'",
                String::class.java,
            ),
        )
        assertEquals(
            false,
            db.queryForObject(
                "select rolcanlogin or rolsuper or rolbypassrls from pg_roles where rolname='hris_directory_capture'",
                Boolean::class.java,
            ),
        )
        assertEquals(
            false,
            db.queryForObject(
                "select pg_has_role('hris_worker_test','hris_directory_capture','MEMBER')",
                Boolean::class.java,
            ),
        )
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        source.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("set role hris_directory_capture")
                statement
                    .executeQuery("select company_id,id,person_id from employments limit 1")
                    .use {}
                for (query in
                    listOf(
                        "select employee_number from employments limit 1",
                        "select legal_name from persons limit 1",
                        "select name from directory_employee_records limit 1",
                    )) {
                    val error =
                        assertThrows(java.sql.SQLException::class.java) {
                            statement.executeQuery(query).use {}
                        }
                    assertEquals("42501", error.sqlState)
                }
            }
        }
    }

    @Test
    fun changingTheCompanyTimezoneReevaluatesAnAlreadyScheduledStart() {
        val f = fixture(start = "2026-10-02")
        val at = Instant.parse("2026-10-01T16:00:00Z")
        success(useCase(at = at).execute())
        assertNull(record(f))
        database()
            .update(
                "update companies set timezone='Pacific/Kiritimati',version=version+1 where id=?",
                f.company,
            )
        assertEquals(1, success(useCase(at = at).execute()))
        assertNotNull(record(f)?.employee)
    }

    @Test
    fun aPassIsBoundedAndTwoWorkersSkipEachOthersLockedRows() {
        val f = fixture()
        repeat(200) { fixture(company = f.company) }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val adapter =
            object : DirectoryProjectionDataSource by source {
                override fun lockDue(company: UUID, now: Instant, limit: Int): List<UUID> {
                    val ids = source.lockDue(company, now, limit)
                    assertEquals(200, ids.size)
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    return ids
                }
            }
        Executors.newSingleThreadExecutor().use { executor ->
            val first = executor.submit<Result<Int>> { useCase(adapter).execute() }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertEquals(1, success(useCase().execute()))
                release.countDown()
                assertEquals(200, success(first.get(10, TimeUnit.SECONDS)))
                assertEquals(0, success(useCase().execute()))
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun aPassDoesNotExpandItsCompanyScopeBeyondTheDiscoveryLimit() {
        repeat(5) { fixture() }
        assertEquals(4, success(useCase().execute()))
        assertEquals(1, success(useCase().execute()))
        assertEquals(0, success(useCase().execute()))
    }

    @Test
    fun aWriteWaitingForProjectionCompletionIsStillDueAfterThatCompletion() {
        val f = fixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val adapter =
            object : DirectoryProjectionDataSource by source {
                override fun schedule(company: UUID, id: UUID, next: Instant?) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    source.schedule(company, id, next)
                }
            }
        Executors.newFixedThreadPool(2).use { executor ->
            val projection = executor.submit<Result<Int>> { useCase(adapter).execute() }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val write =
                    executor.submit<Int> {
                        database()
                            .update(
                                "/* directory-late-source */ update persons set legal_name='Concurrent change',version=version+1 where id=?",
                                f.person,
                            )
                    }
                val blocked =
                    (1..200).any {
                        val waiting =
                            database()
                                .queryForObject(
                                    "select count(*) from pg_stat_activity where wait_event_type='Lock' and query like '/* directory-late-source */%'",
                                    Int::class.java,
                                )!! > 0
                        if (!waiting) Thread.sleep(10)
                        waiting
                    }
                assertTrue(
                    blocked,
                    "The source transaction must be waiting on the worker's queue row",
                )
                release.countDown()
                assertEquals(1, success(projection.get(10, TimeUnit.SECONDS)))
                assertEquals(1, write.get(10, TimeUnit.SECONDS))
                assertEquals(1, success(useCase().execute()))
                assertEquals("Concurrent change", record(f)?.employee?.name)
                assertEquals(1L, record(f)?.version)
            } finally {
                release.countDown()
            }
        }
    }

    @Test
    fun stoppingDuringProjectionRollsBackRecordsEventsAndScheduleAndReleasesTheTimer() {
        val f = fixture()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val adapter =
            object : DirectoryProjectionDataSource by source {
                override fun schedule(company: UUID, id: UUID, next: Instant?) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                    source.schedule(company, id, next)
                }
            }
        val timer =
            ThreadPoolTaskScheduler().apply {
                poolSize = 1
                setRemoveOnCancelPolicy(true)
                setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
                setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
                setWaitForTasksToCompleteOnShutdown(false)
                setAwaitTerminationSeconds(3)
                initialize()
            }
        val worker = CompanyDirectoryWorker(useCase(adapter), timer, Duration.ofMillis(20))
        try {
            worker.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            worker.stop()
            assertTrue(timer.scheduledThreadPoolExecutor.awaitTermination(3, TimeUnit.SECONDS))
            assertTrue(timer.scheduledThreadPoolExecutor.queue.isEmpty())
            assertFalse(worker.isRunning)
            assertNull(record(f))
            assertEquals(0, changes(f))
            assertEquals(1, success(useCase().execute()))
            assertNotNull(record(f)?.employee)
            assertThrows(IllegalStateException::class.java) { worker.start() }
        } finally {
            release.countDown()
            worker.stop()
        }
    }
}
