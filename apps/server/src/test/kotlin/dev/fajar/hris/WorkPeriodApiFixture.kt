package dev.fajar.hris

import dev.fajar.hris.core.database.PostgresTransactionRunner
import dev.fajar.hris.core.database.datasources.PostgresChangeJournalDataSource
import dev.fajar.hris.core.database.repositories.PostgresChangeJournalRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.policies.PermissionCatalog
import dev.fajar.hris.jobs.data.datasources.PostgresJobDataSource
import dev.fajar.hris.jobs.data.repositories.PostgresJobRepository
import dev.fajar.hris.jobs.domain.entities.*
import dev.fajar.hris.jobs.domain.usecases.LeaseJobs
import java.net.http.HttpClient
import java.net.http.HttpResponse
import java.time.*
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

@Import(TestClockConfiguration::class)
abstract class WorkPeriodApiFixture : ApiIntegrationTest() {
    @Autowired protected lateinit var clock: MutableTestClock
    private val companies = mutableListOf<UUID>()

    protected data class Fixture(
        val company: UUID,
        val employee: UUID,
        val employeeAccount: UUID,
        val holiday: UUID,
        val admin: HttpClient,
        val csrf: String,
        val employeeClient: HttpClient,
        val employeeCsrf: String,
        val actor: Actor,
    ) {
        val path: String
            get() = "/api/v1/companies/$company/workforce"
    }

    @AfterEach
    fun settleFixtureJobs() {
        companies.forEach {
            database()
                .update(
                    """update background_jobs set status='CANCELLED',finished_at=now(),lease_owner=null,lease_token=null,lease_until=null
            where company_id=? and status in ('QUEUED','RUNNING')""",
                    it,
                )
        }
    }

    protected fun fixture(email: String = "admin@example.test"): Fixture {
        clock.set(Instant.parse("2026-10-01T03:00:00Z"))
        val admin = client()
        val csrf = login(admin, email)
        val account =
            UUID.fromString(
                json.readTree(get(admin, "/api/v1/me").body()).get("account").get("id").asString()
            )
        val companyResult =
            command(
                admin,
                "/api/v1/companies",
                json.writeValueAsString(
                    mapOf(
                        "code" to "C${UUID.randomUUID().toString().take(8)}",
                        "name" to "Closing fixture",
                        "timezone" to "Asia/Jakarta",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, companyResult.statusCode(), companyResult.body())
        val company = UUID.fromString(json.readTree(companyResult.body()).get("id").asString())
        companies += company
        val personAccount = UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Example Employee',password_hash from accounts where email='admin@example.test'",
                personAccount,
                "$personAccount@example.test",
            )
        database()
            .update(
                "insert into company_memberships(company_id,account_id) values(?,?)",
                company,
                personAccount,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'attendance.self.record')",
                company,
                personAccount,
            )
        val employee = UUID.randomUUID()
        val created =
            command(
                admin,
                "/api/v1/companies/$company/employees",
                json.writeValueAsString(
                    mapOf(
                        "id" to employee,
                        "employeeNumber" to "E001",
                        "person" to
                            mapOf(
                                "id" to UUID.randomUUID(),
                                "accountId" to personAccount,
                                "legalName" to "Example Employee",
                                "nationality" to "ID",
                            ),
                        "terms" to
                            mapOf(
                                "effectiveFrom" to "2026-01-01",
                                "startDate" to "2026-01-01",
                                "contract" to "PERMANENT",
                                "status" to "ACTIVE",
                            ),
                        "reason" to "Onboarding",
                    )
                ),
                csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, created.statusCode(), created.body())
        val holiday = UUID.randomUUID()
        val result =
            command(
                admin,
                "/api/v1/companies/$company/workforce/holidays/$holiday",
                """{"workDate":"2026-09-01","name":"Example holiday","reason":"Calendar setup"}""",
                csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, result.statusCode(), result.body())
        val employeeClient = client()
        val employeeCsrf = login(employeeClient, "$personAccount@example.test")
        return Fixture(
            company,
            employee,
            personAccount,
            holiday,
            admin,
            csrf,
            employeeClient,
            employeeCsrf,
            Actor(
                account,
                company,
                PermissionCatalog.companyAdministrator,
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = 0,
            ),
        )
    }

    protected fun close(
        f: Fixture,
        version: Long = 0,
        key: UUID = UUID.randomUUID(),
        month: String = "2026-09",
    ): HttpResponse<String> =
        command(
            f.admin,
            "${f.path}/periods/$month/close",
            """{"expectedVersion":$version,"reason":"Monthly attendance review"}""",
            f.csrf,
            key,
        )

    protected fun begin(f: Fixture, version: Long = 0): JobLease {
        val response = close(f, version)
        assertEquals(200, response.statusCode(), response.body())
        return claim().single { it.job.request.companyId == f.company }
    }

    protected fun claim(): List<JobLease> {
        database()
            .execute(
                """DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='hris_period_worker') THEN
            CREATE ROLE hris_period_worker LOGIN PASSWORD 'fixture-worker-only' INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
            END IF; END $$"""
            )
        database().execute("GRANT hris_worker_capability TO hris_period_worker")
        database().execute("GRANT USAGE ON SCHEMA public TO hris_period_worker")
        database()
            .execute(
                "GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO hris_period_worker"
            )
        val source =
            DriverManagerDataSource(postgres.jdbcUrl, "hris_period_worker", "fixture-worker-only")
        val jdbc = JdbcTemplate(source)
        val sql =
            DSL.using(
                TransactionAwareDataSourceProxy(source),
                SQLDialect.POSTGRES,
                Settings().withExecuteLogging(false),
            )
        val jobs = PostgresJobRepository(PostgresJobDataSource(sql), json)
        val tx = PostgresTransactionRunner(JdbcTransactionManager(source), jdbc)
        val result =
            LeaseJobs(
                    jobs,
                    tx,
                    PostgresChangeJournalRepository(PostgresChangeJournalDataSource(sql, json)),
                    clock,
                    JobRetryPolicy(),
                )
                .execute(UUID.randomUUID(), 2, 60, setOf(JobKind.WORKFORCE_CLOSE))
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    protected fun period(f: Fixture) =
        json.readTree(get(f.admin, "${f.path}/periods?from=2026-09&until=2026-09").body())[0]

    protected fun capture(f: Fixture, id: UUID = UUID.randomUUID()): UUID {
        val response =
            command(
                f.employeeClient,
                "${f.path}/employees/${f.employee}/attendance",
                json.writeValueAsString(
                    mapOf(
                        "id" to id,
                        "workDate" to "2026-09-30",
                        "kind" to "CLOCK_IN",
                        "capturedAt" to "2026-09-30T01:00:00Z",
                        "deviceId" to UUID.randomUUID(),
                        "offline" to true,
                    )
                ),
                f.employeeCsrf,
                UUID.randomUUID(),
            )
        assertEquals(200, response.statusCode(), response.body())
        return id
    }

    protected fun reject(f: Fixture, id: UUID) =
        command(
            f.admin,
            "${f.path}/attendance/$id/review",
            """{"version":0,"decision":"REJECT","reason":"Verified invalid evidence"}""",
            f.csrf,
            UUID.randomUUID(),
        )

    protected fun cancel(f: Fixture, lease: JobLease) {
        val path = "/api/v1/companies/${f.company}/jobs/${lease.job.request.id}"
        val version = json.readTree(get(f.admin, path).body()).get("version").asLong()
        val response = post(f.admin, "$path/cancel", """{"expectedVersion":$version}""", f.csrf)
        assertEquals(200, response.statusCode(), response.body())
    }
}
