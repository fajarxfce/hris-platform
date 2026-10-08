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
import dev.fajar.hris.people.domain.usecases.*
import java.net.http.HttpClient
import java.util.UUID
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.jdbc.support.JdbcTransactionManager

abstract class EmployeeImportApiFixture : PeopleApiFixture() {
    @Autowired protected lateinit var preview: AdvanceEmployeeImportPreview
    @Autowired protected lateinit var apply: AdvanceEmployeeImportApply
    @Autowired protected lateinit var abort: AbortEmployeeImport
    protected val header = "employee_number,legal_name,nationality,start_date,contract"
    private val companies = mutableListOf<UUID>()

    protected data class ImportFixture(
        val browser: HttpClient,
        val csrf: String,
        val company: UUID,
        val actor: Actor,
    ) {
        val path: String
            get() = "/api/v1/companies/$company/employee-imports"
    }

    protected fun fixture(): ImportFixture {
        val browser = client()
        val csrf = login(browser)
        val company = company(browser, csrf)
        companies += company
        val account =
            database()
                .queryForObject(
                    "select id from accounts where email='admin@example.test'",
                    UUID::class.java,
                )!!
        val version =
            database()
                .queryForObject(
                    "select security_version from accounts where id=?",
                    Long::class.java,
                    account,
                )!!
        return ImportFixture(
            browser,
            csrf,
            company,
            Actor(
                account,
                company,
                PermissionCatalog.companyAdministrator,
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = version,
            ),
        )
    }

    @AfterEach
    fun settleImportJobs() {
        companies.forEach {
            database()
                .update(
                    """update background_jobs set status='CANCELLED',finished_at=now(),lease_owner=null,lease_token=null,lease_until=null where company_id=? and status in ('QUEUED','RUNNING')""",
                    it,
                )
        }
    }

    protected fun csv(vararg numbers: String) =
        header + "\n" + numbers.joinToString("\n") { "$it,Employee $it,ID,2026-01-01,PERMANENT" }

    protected fun start(
        f: ImportFixture,
        content: String,
        id: UUID = UUID.randomUUID(),
        key: UUID = UUID.randomUUID(),
        fileName: String = "employees.csv",
    ) =
        command(
            f.browser,
            f.path,
            json.writeValueAsString(
                mapOf(
                    "id" to id,
                    "fileName" to fileName,
                    "csv" to content,
                    "reason" to "Initial employee import",
                )
            ),
            f.csrf,
            key,
        )

    protected fun begin(f: ImportFixture, content: String): UUID {
        val id = UUID.randomUUID()
        val result = start(f, content, id)
        assertEquals(200, result.statusCode(), result.body())
        return id
    }

    protected fun confirm(
        f: ImportFixture,
        id: UUID,
        version: Long = 1,
        partial: Boolean = false,
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            f.browser,
            "${f.path}/$id/apply",
            json.writeValueAsString(
                mapOf(
                    "expectedVersion" to version,
                    "allowPartial" to partial,
                    "reason" to "Import preview reviewed",
                )
            ),
            f.csrf,
            key,
        )

    protected fun changeImport(
        f: ImportFixture,
        id: UUID,
        action: String,
        version: Long,
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            f.browser,
            "${f.path}/$id/$action",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "reason" to "Import operation reviewed")
            ),
            f.csrf,
            key,
        )

    protected fun details(f: ImportFixture, id: UUID) =
        json.readTree(get(f.browser, "${f.path}/$id").body())

    protected fun rows(f: ImportFixture, id: UUID) =
        json.readTree(get(f.browser, "${f.path}/$id/rows").body()).get("items")

    protected fun claim(kind: JobKind): List<JobLease> {
        database()
            .execute(
                """DO ${'$'}${'$'} BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='hris_import_worker') THEN
            CREATE ROLE hris_import_worker LOGIN PASSWORD 'fixture-worker-only' INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS; END IF; END ${'$'}${'$'}"""
            )
        database().execute("GRANT hris_worker_capability TO hris_import_worker")
        database().execute("GRANT USAGE ON SCHEMA public TO hris_import_worker")
        database()
            .execute(
                "GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO hris_import_worker"
            )
        val source =
            DriverManagerDataSource(postgres.jdbcUrl, "hris_import_worker", "fixture-worker-only")
        val sql =
            DSL.using(
                TransactionAwareDataSourceProxy(source),
                SQLDialect.POSTGRES,
                Settings().withExecuteLogging(false),
            )
        val jobs = PostgresJobRepository(PostgresJobDataSource(sql), json)
        val transactions =
            PostgresTransactionRunner(JdbcTransactionManager(source), JdbcTemplate(source))
        val result =
            LeaseJobs(
                    jobs,
                    transactions,
                    PostgresChangeJournalRepository(PostgresChangeJournalDataSource(sql, json)),
                    clock,
                    JobRetryPolicy(),
                )
                .execute(UUID.randomUUID(), 2, 120, setOf(kind))
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    protected fun lease(id: UUID, kind: JobKind) =
        claim(kind).single { it.job.request.values["importId"] == id.toString() }

    protected fun advance(f: ImportFixture, lease: JobLease) =
        when (lease.job.request.kind) {
            JobKind.EMPLOYEE_IMPORT_PREVIEW -> preview.execute(f.actor, lease)
            JobKind.EMPLOYEE_IMPORT_APPLY -> apply.execute(f.actor, lease)
            else -> error("Unexpected fixture job")
        }

    protected fun drain(f: ImportFixture, lease: JobLease) {
        repeat(lease.job.request.totalItems + 1) {
            val result = advance(f, lease)
            assertTrue(result is Result.Success, result.toString())
            if ((result as Result.Success).value.finished) return
        }
        error("Import exceeded its bounded fixture step budget")
    }

    protected fun createNumber(f: ImportFixture, number: String) {
        val result =
            command(
                f.browser,
                "/api/v1/companies/${f.company}/employees",
                json.writeValueAsString(
                    mapOf(
                        "id" to UUID.randomUUID(),
                        "employeeNumber" to number,
                        "person" to
                            mapOf(
                                "id" to UUID.randomUUID(),
                                "legalName" to "Existing employee",
                                "nationality" to "ID",
                            ),
                        "terms" to terms(),
                        "reason" to "Administrative onboarding",
                    )
                ),
                f.csrf,
                UUID.randomUUID(),
            )
        assertEquals(200, result.statusCode(), result.body())
    }
}
