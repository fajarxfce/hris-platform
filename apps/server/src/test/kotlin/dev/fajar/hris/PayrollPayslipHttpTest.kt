package dev.fajar.hris

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.jobs.domain.entities.*
import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollPayslipHttpTest : PayrollTaxContinuityApiFixture() {
    @Test
    fun onlyPublishedCalculationsAreReadableAndTheirLabelsAndAmountsRemainFrozen() {
        val f = approved()
        val p = f.calculation.people.payroll
        val path = "/api/v1/companies/${p.company}/payroll/payslips"
        assertEquals(0, payrollBody(get(p.owner.client, path))["items"].size())
        val retained = runView(f.calculation, f.run, "/employees/${p.employee}")
        val lease = beginFinalization(f)
        assertEquals(0, payrollBody(get(p.owner.client, path))["items"].size())
        assertEquals(Result.Success(JobStep(1, true)), stepFinalization(f, lease))
        val response = get(p.owner.client, path)
        assertTrue(response.headers().allValues("Cache-Control").any { it.contains("no-store") })
        val list = payrollBody(response)
        assertEquals(1, list["items"].size())
        assertTrue(list["nextCursor"].isNull)
        val summary = list["items"][0]
        assertEquals("IDR", summary["currency"].asString())
        assertEquals(0, summary["version"].asLong())
        assertEquals(p.employee.toString(), summary["employeeId"].asString())
        assertFalse(summary.has("calculation"))
        val detail = payrollBody(get(p.owner.client, "$path/${summary["id"].asString()}"))
        assertEquals(summary, detail["summary"])
        assertEquals(retained["calculation"], detail["calculation"])
        assertFalse(detail.has("facts"))
        assertFalse(detail.has("reason"))
        assertEquals(retained["item"]["target"]["employeeName"], summary["employeeName"])
        assertEquals(
            0,
            BigDecimal(detail["calculation"]["tax"]["takeHome"].asString())
                .compareTo(BigDecimal(summary["takeHome"].asString())),
        )
        database()
            .update(
                "update companies set name='Changed issuer',version=version+1 where id=?",
                p.company,
            )
        payrollBody(
            command(
                p.admin,
                "/api/v1/companies/${p.company}/employees/${p.employee}/profile",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to 0,
                        "legalName" to "Changed employee",
                        "nationality" to "ID",
                        "reason" to "Reviewed personnel change",
                    )
                ),
                p.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
        assertEquals(detail, payrollBody(get(p.owner.client, "$path/${summary["id"].asString()}")))
        assertEquals(
            detail,
            payrollBody(get(p.operator.client, "$path/${summary["id"].asString()}")),
        )
    }

    @Test
    fun selfScopeCannotReadAnotherEmploymentAndAdministrationDoesNotGrantFinancialAccess() {
        val f = approved()
        val p = f.calculation.people.payroll
        assertTrue(stepFinalization(f, beginFinalization(f)) is Result.Success)
        val path = "/api/v1/companies/${p.company}/payroll/payslips"
        val id = payrollBody(get(p.owner.client, path))["items"][0]["id"].asString()
        val unbound = payrollMember(p.company, setOf("company.read", "payroll.self.read"))
        assertEquals(0, payrollBody(get(unbound.client, path))["items"].size())
        payrollError(get(unbound.client, "$path/$id"), 404, "payroll_payslip_not_found")
        payrollError(get(unbound.client, "$path?employeeId=${p.employee}"), 403, "access_denied")
        assertEquals(
            1,
            payrollBody(get(p.owner.client, "$path?employeeId=${p.employee}"))["items"].size(),
        )
        assertEquals(
            0,
            payrollBody(get(p.operator.client, "$path?employeeId=${UUID.randomUUID()}"))["items"]
                .size(),
        )
        for (browser in
            listOf(
                p.admin,
                f.finalizer.client,
                f.calculation.people.preparer.client,
                f.calculation.people.reviewer.client,
            )) {
            payrollError(get(browser, path), 403, "access_denied")
            payrollError(get(browser, "$path/$id"), 403, "access_denied")
        }
        val foreign = payrollFixture()
        payrollError(get(foreign.owner.client, "$path/$id"), 403, "company_access_denied")
        payrollError(
            get(
                foreign.operator.client,
                "/api/v1/companies/${foreign.company}/payroll/payslips/$id",
            ),
            404,
            "payroll_payslip_not_found",
        )
    }

    @Test
    fun newestFirstKeysetPagesRetainOlderMonthsWithoutLoadingCalculationJson() {
        val first = calculationFixture()
        reviewTemplate(first)
        val (_, september) = publishTaxRun(first, beginTaxRun(first))
        val second = followingMonth(first, YearMonth.of(2026, 10))
        val (_, october) = publishTaxRun(second, beginTaxRun(second))
        val p = second.people.payroll
        val path = "/api/v1/companies/${p.company}/payroll/payslips"
        val page = payrollBody(get(p.owner.client, "$path?limit=1"))
        assertEquals(october.toString(), page["items"][0]["id"].asString())
        val next =
            payrollBody(get(p.owner.client, "$path?limit=1&after=${page["nextCursor"].asString()}"))
        assertEquals(september.toString(), next["items"][0]["id"].asString())
        assertTrue(next["nextCursor"].isNull)
        val filtered = payrollBody(get(p.owner.client, "$path?from=2026-09&until=2026-09"))
        assertEquals(september.toString(), filtered["items"][0]["id"].asString())
        for (query in
            listOf(
                "limit=201",
                "limit=0",
                "after=invalid",
                "after=2026-12:${UUID.randomUUID()}&until=2026-09",
            )) payrollError(get(p.owner.client, "$path?$query"), 422, "invalid_page")
        for (query in
            listOf(
                "from=2023-12&until=2026-12",
                "from=2026-10&until=2026-09",
                "from=2024-01&until=2027-01",
            )) payrollError(
            get(p.owner.client, "$path?$query"),
            422,
            "invalid_payroll_payslip_range",
        )
        assertEquals(400, get(p.owner.client, "$path?from=not-a-month").statusCode())
    }
}
