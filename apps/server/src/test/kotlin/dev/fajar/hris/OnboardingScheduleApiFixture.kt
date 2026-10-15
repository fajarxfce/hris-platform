package dev.fajar.hris

import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.database.datasources.PostgresChangeJournalDataSource
import dev.fajar.hris.core.database.repositories.PostgresChangeJournalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.data.datasources.PostgresJobDataSource
import dev.fajar.hris.jobs.data.repositories.PostgresJobRepository
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.usecases.LeaseJobs
import dev.fajar.hris.people.domain.entities.OnboardingScheduleSelection
import dev.fajar.hris.people.domain.usecases.*
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager
import tools.jackson.databind.JsonNode

@Import(OnboardingScheduleProbeConfiguration::class)
abstract class OnboardingScheduleApiFixture : LifecycleApiFixture() {
    @Autowired protected lateinit var executeSchedule: ExecuteOnboardingSchedule
    @Autowired protected lateinit var abortSchedule: AbortOnboardingSchedule
    @Autowired protected lateinit var scheduleProbe: OnboardingScheduleProbe
    @Autowired protected lateinit var transactions: TransactionRunner
    private val ownedCompanies = mutableSetOf<UUID>()
    protected val required =
        setOf("company.read", "people.read", "people.lifecycle.manage", "people.lifecycle.read")

    protected data class Fixture(
        val browser: HttpClient,
        val csrf: String,
        val company: UUID,
        val employee: UUID,
        val templateId: UUID,
        val assignee: UUID,
        val actor: Actor,
        val email: String,
        val admittedAt: Instant,
        val timezone: String,
        val firstDay: LocalDate,
        val startsOn: LocalDate,
    ) {
        val path
            get() = "/api/v1/companies/$company/employees/$employee/onboarding-schedules"

        val reviewPath
            get() = "$path/review?templateId=$templateId&targetDate=$firstDay&startsOn=$startsOn"

        val selection
            get() = OnboardingScheduleSelection(templateId, firstDay, startsOn)
    }

    protected data class Prepared(
        val id: UUID,
        val caseId: UUID,
        val operation: UUID,
        val body: String,
    )

    @AfterEach
    fun stopOnboardingFixtures() {
        scheduleProbe.clear()
        for (company in ownedCompanies) database()
            .update(
                "update background_jobs set status='CANCELLED',finished_at=now(),lease_owner=null,lease_token=null,lease_until=null where company_id=? and status in ('QUEUED','RUNNING')",
                company,
            )
    }

    protected fun fixture(
        future: Boolean = false,
        operator: Boolean = false,
        timezone: String = "Asia/Jakarta",
        admitted: Instant? = null,
    ): Fixture {
        val at =
            admitted
                ?: Instant.now()
                    .truncatedTo(ChronoUnit.MICROS)
                    .minus(if (future) Duration.ZERO else Duration.ofDays(2))
        clock.set(at)
        val owner = client()
        val ownerCsrf = login(owner)
        val company = company(owner, ownerCsrf, timezone)
        ownedCompanies += company
        val firstDay = at.atZone(ZoneId.of(timezone)).toLocalDate().plusDays(3)
        val employee = employee(owner, ownerCsrf, company, start = firstDay.toString())
        val template = template(owner, ownerCsrf, company, "ONBOARDING", optional = true)
        val assignee = user().also { member(company, it) }
        val account =
            if (operator) user().also { member(company, it, required) }
            else UUID.fromString(ok(get(owner, "/api/v1/me"))["account"]["id"].asString())
        val email = if (operator) "$account@example.test" else "admin@example.test"
        val browser = if (operator) client() else owner
        val csrf = if (operator) login(browser, email) else ownerCsrf
        val credential =
            database()
                .queryForObject(
                    "select security_version from accounts where id=?",
                    Long::class.java,
                    account,
                )!!
        val actor =
            Actor(account, company, required, at, UUID.randomUUID(), credentialVersion = credential)
        return Fixture(
            browser,
            csrf,
            company,
            employee,
            template,
            assignee,
            actor,
            email,
            at,
            timezone,
            firstDay,
            firstDay.minusDays(2),
        )
    }

    protected fun ok(response: HttpResponse<String>): JsonNode {
        assertEquals(200, response.statusCode(), response.body())
        return json.readTree(response.body())
    }

    protected fun prepare(
        f: Fixture,
        assignees: Map<String, UUID> = mapOf("equipment" to f.assignee),
    ): Prepared {
        val review = ok(get(f.browser, f.reviewPath))
        assertTrue(review["blockers"].isEmpty, review.toString())
        val id = UUID.randomUUID()
        val caseId = UUID.randomUUID()
        return Prepared(
            id,
            caseId,
            UUID.randomUUID(),
            json.writeValueAsString(
                mapOf(
                    "id" to id,
                    "caseId" to caseId,
                    "templateId" to f.templateId,
                    "employmentVersion" to review["employmentVersion"].asLong(),
                    "templateVersion" to review["template"]["version"].asLong(),
                    "basisRevision" to review["basisRevision"].asString(),
                    "targetDate" to f.firstDay,
                    "startsOn" to f.startsOn,
                    "assignees" to assignees,
                    "reason" to "Reviewed scheduled onboarding",
                )
            ),
        )
    }

    protected fun submit(f: Fixture, prepared: Prepared) =
        command(f.browser, f.path, prepared.body, f.csrf, prepared.operation)

    protected fun scheduled(f: Fixture): Prepared = prepare(f).also { ok(submit(f, it)) }

    protected fun details(f: Fixture, id: UUID, browser: HttpClient = f.browser) =
        ok(get(browser, "${f.path}/$id"))

    protected fun freshReader(f: Fixture): HttpClient = client().also { login(it, f.email) }

    protected fun jobCommand(
        f: Fixture,
        id: UUID,
        action: String,
        body: JsonNode = details(f, id),
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            f.browser,
            "${f.path}/$id/$action",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to body["version"].asLong(),
                    "expectedJobVersion" to body["job"]["version"].asLong(),
                    "reason" to "Reviewed schedule $action",
                )
            ),
            f.csrf,
            key,
        )

    protected fun count(f: Fixture, table: String): Int =
        database()
            .queryForObject(
                "select count(*) from $table where company_id=?",
                Int::class.java,
                f.company,
            )!!

    protected fun employmentStatus(f: Fixture): String =
        database()
            .queryForObject(
                "select status from employment_revisions where company_id=? and employment_id=? order by revision desc limit 1",
                String::class.java,
                f.company,
                f.employee,
            )!!

    protected fun claim(): List<JobLease> {
        database()
            .execute(
                """DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='hris_onboarding_fixture_worker') THEN CREATE ROLE hris_onboarding_fixture_worker LOGIN PASSWORD 'fixture-worker-only' INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS; END IF; END $$"""
            )
        database().execute("GRANT hris_worker_capability TO hris_onboarding_fixture_worker")
        database().execute("GRANT USAGE ON SCHEMA public TO hris_onboarding_fixture_worker")
        database()
            .execute(
                "GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO hris_onboarding_fixture_worker"
            )
        val data =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "hris_onboarding_fixture_worker",
                "fixture-worker-only",
            )
        val jdbc = JdbcTemplate(data)
        val sql =
            DSL.using(
                TransactionAwareDataSourceProxy(data),
                SQLDialect.POSTGRES,
                Settings().withExecuteLogging(false),
            )
        val jobs = PostgresJobRepository(PostgresJobDataSource(sql), json)
        val tx = PostgresTransactionRunner(JdbcTransactionManager(data), jdbc)
        val result =
            LeaseJobs(
                    jobs,
                    tx,
                    PostgresChangeJournalRepository(PostgresChangeJournalDataSource(sql, json)),
                    clock,
                    JobRetryPolicy(),
                )
                .execute(UUID.randomUUID(), 2, 120, setOf(JobKind.PEOPLE_ONBOARD_START))
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    protected fun execute(f: Fixture, lease: JobLease): Result<JobStep> {
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS))
        return executeSchedule.execute(f.actor, lease)
    }
}
