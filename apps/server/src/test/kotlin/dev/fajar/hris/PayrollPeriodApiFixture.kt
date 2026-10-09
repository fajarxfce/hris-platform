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
import dev.fajar.hris.payroll.domain.usecases.*
import dev.fajar.hris.workforce.domain.usecases.AdvanceWorkPeriodClose
import java.time.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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

@Import(PayrollPeriodProbeConfiguration::class)
abstract class PayrollPeriodApiFixture : PayrollApiFixture() {
    @Autowired protected lateinit var periodProbe: PayrollPeriodProbe
    @Autowired protected lateinit var advanceWork: AdvanceWorkPeriodClose
    @Autowired protected lateinit var createPeriod: CreatePayrollPeriod
    @Autowired protected lateinit var saveInput: SavePayrollInput
    private val periodCompanies = ConcurrentHashMap.newKeySet<UUID>()

    protected data class ProcessingFixture(
        val payroll: PayrollFixture,
        val preparer: PayrollMember,
        val reviewer: PayrollMember,
        val actor: Actor,
    )

    protected data class WorkSource(val job: UUID, val version: Long)

    @AfterEach
    fun resetPeriodHooks() {
        periodProbe.clear()
        for (company in periodCompanies) database()
            .update(
                "update background_jobs set status='CANCELLED',finished_at=now(),lease_owner=null,lease_token=null,lease_until=null where company_id=? and status in ('QUEUED','RUNNING')",
                company,
            )
        periodCompanies.clear()
    }

    protected fun processingFixture(): ProcessingFixture {
        val f = payrollFixture()
        periodCompanies.add(f.company)
        val account =
            UUID.fromString(
                json.readTree(get(f.admin, "/api/v1/me").body())["account"]["id"].asString()
            )
        val version =
            database()
                .queryForObject(
                    "select security_version from accounts where id=?",
                    Long::class.java,
                    account,
                )!!
        return ProcessingFixture(
            f,
            payrollMember(f.company, setOf("company.read", "payroll.calculate", "payroll.review")),
            payrollMember(f.company, setOf("company.read", "payroll.review")),
            Actor(
                account,
                f.company,
                PermissionCatalog.companyAdministrator,
                clock.instant(),
                UUID.randomUUID(),
                credentialVersion = version,
            ),
        )
    }

    protected fun periodBody(
        f: ProcessingFixture,
        id: UUID = UUID.randomUUID(),
        employees: Set<UUID> = setOf(f.payroll.employee),
        changes: Map<String, Any?> = emptyMap(),
    ): String =
        json.writeValueAsString(
            mapOf(
                "id" to id,
                "earningsMonth" to "2026-09",
                "plannedPaymentDate" to "2026-10-02",
                "employeeIds" to employees,
                "reason" to "Prepare September payroll",
            ) + changes
        )

    protected fun periodsPath(f: ProcessingFixture) =
        "/api/v1/companies/${f.payroll.company}/payroll/periods"

    protected fun periodCreate(
        f: ProcessingFixture,
        body: String = periodBody(f),
        key: UUID = UUID.randomUUID(),
        member: PayrollMember = f.preparer,
    ) = command(member.client, periodsPath(f), body, member.csrf, key)

    protected fun periodCancel(
        f: ProcessingFixture,
        id: UUID,
        version: Long = 0,
        key: UUID = UUID.randomUUID(),
    ) =
        command(
            f.preparer.client,
            periodsPath(f) + "/$id/cancel",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "reason" to "Replace payroll draft")
            ),
            f.preparer.csrf,
            key,
        )

    protected fun workSource(f: ProcessingFixture, employee: UUID = f.payroll.employee) =
        payrollBody(
            get(
                f.preparer.client,
                "/api/v1/companies/${f.payroll.company}/payroll/employees/$employee/work-source?month=2026-09",
            )
        )

    protected fun closeWork(f: ProcessingFixture, finish: Boolean = true): WorkSource {
        val result =
            command(
                f.payroll.admin,
                "/api/v1/companies/${f.payroll.company}/workforce/periods/2026-09/close",
                """{"expectedVersion":0,"reason":"Verified workforce closing"}""",
                f.payroll.adminCsrf,
                UUID.randomUUID(),
            )
        val id = UUID.fromString(payrollBody(result)["id"].asString())
        val lease = claimPayrollJobs(JobKind.WORKFORCE_CLOSE).single { it.job.request.id == id }
        if (finish) {
            var done = false
            repeat(lease.job.request.totalItems) {
                if (!done) {
                    val step = advanceWork.execute(f.actor, lease)
                    assertTrue(step is Result.Success, step.toString())
                    done = (step as Result.Success).value.finished
                }
            }
            assertTrue(done, "Closing must finish within its declared finite target count")
        }
        val source = workSource(f)
        return WorkSource(id, source["version"].asLong())
    }

    protected fun inputTerms(changes: Map<String, Any?> = emptyMap()): Map<String, Any?> =
        mapOf(
            "earnings" to
                listOf(
                    mapOf(
                        "code" to "BONUS",
                        "name" to "Monthly incentive",
                        "amount" to "1000000",
                        "taxable" to true,
                    )
                ),
            "deductions" to emptyList<Any>(),
            "nonCashTaxable" to "0",
            "dayResolutions" to emptyList<Any>(),
            "reviewReference" to "Reviewed September adjustment evidence",
        ) + changes

    protected fun inputBody(
        source: WorkSource,
        version: Long? = null,
        changes: Map<String, Any?> = emptyMap(),
        termChanges: Map<String, Any?> = emptyMap(),
    ): String =
        json.writeValueAsString(
            mapOf(
                "workJobId" to source.job,
                "expectedWorkPeriodVersion" to source.version,
                "expectedEmploymentVersion" to 0,
                "expectedVersion" to version,
                "terms" to inputTerms(termChanges),
                "reason" to "Prepare monthly payroll inputs",
            ) + changes
        )

    protected fun inputPath(f: ProcessingFixture, employee: UUID = f.payroll.employee) =
        "/api/v1/companies/${f.payroll.company}/payroll/employees/$employee/inputs/2026-09"

    protected fun inputSave(
        f: ProcessingFixture,
        source: WorkSource,
        body: String = inputBody(source),
        key: UUID = UUID.randomUUID(),
        member: PayrollMember = f.preparer,
        employee: UUID = f.payroll.employee,
    ) = command(member.client, inputPath(f, employee), body, member.csrf, key, "PUT")

    protected fun inputVerify(
        f: ProcessingFixture,
        version: Long = 0,
        key: UUID = UUID.randomUUID(),
        member: PayrollMember = f.reviewer,
        employee: UUID = f.payroll.employee,
    ) =
        command(
            member.client,
            inputPath(f, employee) + "/verify",
            json.writeValueAsString(
                mapOf("expectedVersion" to version, "reason" to "Independent monthly input review")
            ),
            member.csrf,
            key,
        )

    protected fun claimPayrollJobs(kind: JobKind, limit: Int = 2): List<JobLease> {
        database()
            .execute(
                """DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='hris_payroll_fixture_worker') THEN CREATE ROLE hris_payroll_fixture_worker LOGIN PASSWORD 'fixture-worker-only' INHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS; END IF; END $$"""
            )
        database().execute("GRANT hris_worker_capability TO hris_payroll_fixture_worker")
        database().execute("GRANT USAGE ON SCHEMA public TO hris_payroll_fixture_worker")
        database()
            .execute(
                "GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO hris_payroll_fixture_worker"
            )
        val data =
            DriverManagerDataSource(
                postgres.jdbcUrl,
                "hris_payroll_fixture_worker",
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
                .execute(UUID.randomUUID(), limit, 120, setOf(kind))
        assertTrue(result is Result.Success, result.toString())
        return (result as Result.Success).value
    }

    protected fun largeRoster(f: ProcessingFixture, size: Int): Set<UUID> {
        require(size in 1..5000)
        val rows =
            database()
                .query(
                    """
                    with context as (select ?::uuid company_id,?::uuid actor_id),
                    ids as materialized (select gen_random_uuid() person_id,gen_random_uuid() employment_id,i from generate_series(1,?) i),
                    people as (insert into persons(id,owner_company_id,legal_name,nationality) select ids.person_id,c.company_id,'Roster fixture '||ids.i,'ID' from ids cross join context c returning id),
                    profiles as (insert into person_profile_revisions(person_id,revision,owner_company_id,legal_name,nationality,actor_id,reason) select p.id,0,c.company_id,'Roster fixture','ID',c.actor_id,'Owned roster fixture' from people p cross join context c),
                    employees as (insert into employments(company_id,id,person_id,employee_number) select c.company_id,ids.employment_id,ids.person_id,'R'||lpad(ids.i::text,6,'0') from ids join people p on p.id=ids.person_id cross join context c returning id)
                    insert into employment_revisions(company_id,employment_id,revision,effective_from,contract_kind,start_date,status,actor_id,reason)
                    select c.company_id,e.id,0,'2026-01-01','PERMANENT','2026-01-01','ACTIVE',c.actor_id,'Owned roster fixture' from employees e cross join context c returning employment_id
                    """
                        .trimIndent(),
                    { rs, _ -> UUID.fromString(rs.getString("employment_id")) },
                    f.payroll.company,
                    f.actor.accountId,
                    size,
                )
        return rows.toSet()
    }
}
