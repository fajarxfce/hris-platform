package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.reporting.domain.repositories.HeadcountReportRepository
import dev.fajar.hris.reporting.domain.usecases.GetHeadcountReport
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class HeadcountReportHttpTest : HeadcountReportApiFixture() {
    @Autowired private lateinit var reports: HeadcountReportRepository
    @Autowired private lateinit var transactions: TransactionRunner
    @Autowired private lateinit var getReport: GetHeadcountReport

    @Test
    fun headcountUsesEffectiveHistoryInclusiveContractEndsAndExplicitStatusDefinitions() {
        val f = fixture()
        val empty = report(f)
        assertEquals(0, empty["employments"].asLong())
        assertEquals(0, empty["persons"].asLong())
        assertEquals("headcount.v1", empty["definitionVersion"].asString())
        assertEquals(f.company.toString(), empty["companyId"].asString())
        assertEquals("2026-10-01", empty["asOf"].asString())
        create(f)
        create(f, mapOf("status" to "PROBATION"))
        create(
            f,
            mapOf("status" to "SUSPENDED", "contract" to "FIXED_TERM", "endDate" to "2026-12-31"),
        )
        create(f, mapOf("contract" to "FIXED_TERM", "endDate" to "2026-10-01"))
        create(f, mapOf("status" to "ENDED", "endDate" to "2026-09-30"))
        create(f, mapOf("startDate" to "2026-11-01", "effectiveFrom" to "2026-11-01"))
        val changed = create(f)
        assertEquals(
            200,
            revise(
                    f.browser,
                    f.csrf,
                    f.company,
                    changed,
                    0,
                    terms("2026-11-01", status = "SUSPENDED"),
                )
                .statusCode(),
        )
        assertEquals(200, cancellation(f.browser, f.csrf, f.company, changed, 1, 1).statusCode())
        val current = report(f)
        assertEquals(5, current["employments"].asLong())
        assertEquals(5, current["persons"].asLong())
        assertEquals(3, current["active"].asLong())
        assertEquals(1, current["probation"].asLong())
        assertEquals(1, current["suspended"].asLong())
        assertEquals(3, current["permanent"].asLong())
        assertEquals(2, current["fixedTerm"].asLong())
        assertEquals(0, report(f, "2025-12-31")["employments"].asLong())
        assertEquals(4, report(f, "2026-10-02")["employments"].asLong())
        assertEquals(5, report(f, "2026-11-01")["employments"].asLong())
        assertEquals(1, report(f, "2026-11-01")["suspended"].asLong())
        assertEquals(
            200,
            revise(
                    f.browser,
                    f.csrf,
                    f.company,
                    changed,
                    2,
                    terms("2026-08-01", status = "SUSPENDED"),
                )
                .statusCode(),
        )
        assertEquals(1, report(f, "2026-07-31")["suspended"].asLong())
        assertEquals(2, report(f, "2026-08-01")["suspended"].asLong())
        assertFalse(current.toString().contains("employeeNumber"))
        assertFalse(current.toString().contains("legalName"))
    }

    @Test
    fun aggregateSeparatesPersonsFromEmploymentsAndRlsCannotReadAnotherCompany() {
        val f = fixture()
        val original = create(f)
        val duplicate = UUID.randomUUID()
        database()
            .update(
                "insert into employments(company_id,id,person_id,employee_number) select company_id,?,person_id,? from employments where company_id=? and id=?",
                duplicate,
                "R${duplicate.toString().take(8)}",
                f.company,
                original,
            )
        database()
            .update(
                "insert into employment_revisions(company_id,employment_id,revision,effective_from,contract_kind,start_date,status,actor_id,reason) values(?,?,0,'2026-01-01','PERMANENT','2026-01-01','ACTIVE',?,'Legacy fixture')",
                f.company,
                duplicate,
                f.account,
            )
        val counts = report(f)
        assertEquals(2, counts["employments"].asLong())
        assertEquals(1, counts["persons"].asLong())
        val other = fixture()
        create(other)
        failure(get(f.browser, "${other.path}?asOf=2026-10-01"), 403, "company_access_denied")
        val isolated =
            transactions.run(f.actor) {
                reports.count(other.company, LocalDate.parse("2026-10-01"))
            } as Result.Success
        assertEquals(0, isolated.value.employments)
        assertEquals(0, isolated.value.persons)
    }

    @Test
    fun reportRequiresBothPermissionsAndFollowsCompanyAvailability() {
        val f = fixture()
        create(f)
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='people.read'",
                f.company,
                f.account,
            )
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'people.team.read'),(?,?,'people.self.read')",
                f.company,
                f.account,
                f.company,
                f.account,
            )
        failure(get(f.browser, "${f.path}?asOf=2026-10-01"), 403, "access_denied")
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'people.read')",
                f.company,
                f.account,
            )
        database()
            .update(
                "delete from membership_permissions where company_id=? and account_id=? and permission='reports.read'",
                f.company,
                f.account,
            )
        failure(get(f.browser, "${f.path}?asOf=2026-10-01"), 403, "access_denied")
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'reports.read')",
                f.company,
                f.account,
            )
        val settings =
            command(
                f.browser,
                "/api/v1/companies/${f.company}/settings/client-policy",
                """{"expectedVersion":null,"activateAt":null,"disabledModules":["REPORTING"],"minimumBuilds":{"android":0,"ios":0,"web":0},"maintenance":null,"reason":"Pause reporting"}""",
                f.csrf,
                UUID.randomUUID(),
                "PUT",
            )
        assertEquals(200, settings.statusCode(), settings.body())
        failure(get(f.browser, "${f.path}?asOf=2026-10-01"), 403, "company_module_disabled")
    }

    @Test
    fun invalidDatesAndTechnicalFailuresUseSafeCodesWhileCancellationPropagates() {
        val f = fixture()
        failure(get(f.browser, "${f.path}?asOf=2101-01-01"), 422, "invalid_report_date")
        failure(get(f.browser, "${f.path}?asOf=not-a-date"), 400, "invalid_request")
        reportProbe.failure.set(IllegalStateException("Private report source fixture"))
        val failed = get(f.browser, "${f.path}?asOf=2026-10-01")
        failure(failed, 500, "database_failure")
        assertFalse(failed.body().contains("Private report source"))
        reportProbe.failure.set(InterruptedException())
        assertThrows(InterruptedException::class.java) {
            getReport.execute(f.actor, LocalDate.parse("2026-10-01"))
        }
        assertEquals(0, report(f)["employments"].asLong())
    }
}
