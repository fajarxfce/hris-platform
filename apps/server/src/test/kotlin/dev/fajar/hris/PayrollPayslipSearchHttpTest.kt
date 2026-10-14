package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.YearMonth
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollPayslipSearchHttpTest : PayrollTaxContinuityApiFixture() {
    @Test
    fun frozenNameAndNumberSearchPrecedesPaginationAndTreatsWildcardsLiterally() {
        val first = calculationFixture()
        rename(first.people.payroll, 0, "Archive_100%")
        reviewTemplate(first)
        val (_, september) = publishTaxRun(first, beginTaxRun(first))
        val second =
            followingMonth(first, YearMonth.of(2026, 10)) {
                rename(it.payroll, 1, "Current employee")
            }
        val (_, october) = publishTaxRun(second, beginTaxRun(second))
        val third =
            followingMonth(second, YearMonth.of(2026, 11)) {
                rename(it.payroll, 2, "Archive third")
            }
        val (_, november) = publishTaxRun(third, beginTaxRun(third))
        val p = third.people.payroll
        rename(p, 3, "New live label")
        val path = "/api/v1/companies/${p.company}/payroll/payslips?from=2026-09&until=2026-11"
        val firstPage =
            payrollBody(get(p.operator.client, "$path&query=${encoded("  aRCHive  ")}&limit=1"))
        assertEquals(1, firstPage["items"].size())
        assertEquals(november.toString(), firstPage["items"][0]["id"].asString())
        val next =
            payrollBody(
                get(
                    p.operator.client,
                    "$path&query=archive&limit=1&after=${firstPage["nextCursor"].asString()}",
                )
            )
        assertEquals(1, next["items"].size())
        assertEquals(september.toString(), next["items"][0]["id"].asString())
        assertTrue(next["nextCursor"].isNull)
        assertEquals("Archive_100%", next["items"][0]["employeeName"].asString())
        for (query in listOf("%", "_", "100%")) {
            val found = payrollBody(get(p.operator.client, "$path&query=${encoded(query)}&limit=1"))
            assertEquals(1, found["items"].size())
            assertEquals(september.toString(), found["items"][0]["id"].asString())
            assertTrue(found["nextCursor"].isNull)
        }
        val current = payrollBody(get(p.operator.client, "$path&query=CURRENT&limit=1"))
        assertEquals(october.toString(), current["items"][0]["id"].asString())
        val employeeNumber = next["items"][0]["employeeNumber"].asString()
        val byNumber =
            payrollBody(
                get(p.operator.client, "$path&query=${encoded(employeeNumber.lowercase())}")
            )
        assertEquals(3, byNumber["items"].size())
        for (query in listOf("New live label", "' OR 1=1 --", "\\", "absent")) {
            val absent =
                payrollBody(get(p.operator.client, "$path&query=${encoded(query)}&limit=1"))
            assertEquals(0, absent["items"].size())
            assertTrue(absent["nextCursor"].isNull)
        }
        assertEquals(
            payrollBody(get(p.operator.client, path)),
            payrollBody(get(p.operator.client, "$path&query=${encoded("   ")}")),
        )
        val narrowed =
            payrollBody(
                get(
                    p.operator.client,
                    "/api/v1/companies/${p.company}/payroll/payslips?from=2026-10&until=2026-10&query=archive",
                )
            )
        assertEquals(0, narrowed["items"].size())
        assertFalse(firstPage["items"][0].has("calculation"))
        assertFalse(firstPage["items"][0].has("facts"))
    }

    @Test
    fun boundedSearchRetainsPublishedDataAndCurrentCompanyAndSelfScopes() {
        val f = approved()
        val p = f.calculation.people.payroll
        val path = "/api/v1/companies/${p.company}/payroll/payslips"
        val search = "$path?query=EXAMPLE&from=2026-09&until=2026-09"
        assertEquals(0, payrollBody(get(p.owner.client, search))["items"].size())
        assertTrue(stepFinalization(f, beginFinalization(f)) is Result.Success)
        assertEquals(1, payrollBody(get(p.owner.client, "$search&scope=SELF"))["items"].size())
        assertEquals(1, payrollBody(get(p.operator.client, search))["items"].size())
        assertEquals(0, payrollBody(get(p.operator.client, "$search&scope=SELF"))["items"].size())
        val unbound = payrollMember(p.company, setOf("company.read", "payroll.self.read"))
        assertEquals(0, payrollBody(get(unbound.client, search))["items"].size())
        payrollError(get(unbound.client, "$search&employeeId=${p.employee}"), 403, "access_denied")
        for (client in
            listOf(
                p.admin,
                f.finalizer.client,
                f.calculation.people.preparer.client,
                f.calculation.people.reviewer.client,
            )) payrollError(get(client, search), 403, "access_denied")
        val foreign = payrollFixture()
        payrollError(get(foreign.operator.client, search), 403, "company_access_denied")
        assertEquals(
            0,
            payrollBody(
                    get(
                        foreign.operator.client,
                        "/api/v1/companies/${foreign.company}/payroll/payslips?query=EXAMPLE",
                    )
                )["items"]
                .size(),
        )
        for (query in listOf("x".repeat(121), "Example\n", "Example\u0000", "Example\u007f")) {
            val invalid = get(p.operator.client, "$path?query=${encoded(query)}")
            payrollError(invalid, 422, "invalid_payroll_payslip_search")
            val body = json.readTree(invalid.body())
            assertEquals("invalid", body["fields"]["query"].asString())
            assertEquals("120", body["parameters"]["maximumLength"].asString())
        }
        assertEquals(200, get(p.operator.client, "$path?query=${"x".repeat(120)}").statusCode())
    }

    private fun encoded(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun rename(p: PayrollFixture, version: Long, name: String) {
        payrollBody(
            command(
                p.admin,
                "/api/v1/companies/${p.company}/employees/${p.employee}/profile",
                json.writeValueAsString(
                    mapOf(
                        "expectedVersion" to version,
                        "legalName" to name,
                        "nationality" to "ID",
                        "reason" to "Review retained payslip labels",
                    )
                ),
                p.adminCsrf,
                UUID.randomUUID(),
                "PUT",
            )
        )
    }
}
