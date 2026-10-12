package dev.fajar.hris

import dev.fajar.hris.core.domain.Result
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollPayslipScopeHttpTest : PayrollFinalizationApiFixture() {
    @Test
    fun explicitSelfScopeNeverExpandsThroughCompanyFinanceGrants() {
        val f = approved()
        val p = f.calculation.people.payroll
        assertTrue(stepFinalization(f, beginFinalization(f)) is Result.Success)
        val path = "/api/v1/companies/${p.company}/payroll/payslips"
        val page = payrollBody(get(p.owner.client, "$path?scope=SELF&limit=1"))
        val id = page["items"][0]["id"].asString()
        assertEquals(1, page["items"].size())
        assertTrue(page["nextCursor"].isNull)
        assertEquals(
            payrollBody(get(p.owner.client, "$path/$id")),
            payrollBody(get(p.owner.client, "$path/$id?scope=SELF")),
        )
        assertEquals(
            payrollBody(get(p.owner.client, "$path/$id/payments")),
            payrollBody(get(p.owner.client, "$path/$id/payments?scope=SELF")),
        )
        val readers =
            listOf(
                p.operator,
                payrollMember(p.company, setOf("company.read", "payroll.read", "payroll.self.read")),
            )
        for (reader in readers) {
            assertEquals(1, payrollBody(get(reader.client, path))["items"].size())
            assertEquals(
                1,
                payrollBody(get(reader.client, "$path?scope=AUTHORIZED"))["items"].size(),
            )
            assertEquals(0, payrollBody(get(reader.client, "$path?scope=SELF"))["items"].size())
            assertEquals(
                0,
                payrollBody(
                        get(
                            reader.client,
                            "$path?scope=SELF&from=2026-09&until=2026-09&after=2026-09:$id",
                        )
                    )["items"]
                    .size(),
            )
            payrollError(
                get(reader.client, "$path?scope=SELF&employeeId=${p.employee}"),
                403,
                "access_denied",
            )
            for (suffix in listOf("", "/payments")) {
                payrollError(
                    get(reader.client, "$path/$id$suffix?scope=SELF"),
                    404,
                    "payroll_payslip_not_found",
                )
                assertEquals(200, get(reader.client, "$path/$id$suffix").statusCode())
            }
        }
        val unknown = UUID.randomUUID()
        payrollError(
            get(p.owner.client, "$path/$unknown?scope=SELF"),
            404,
            "payroll_payslip_not_found",
        )
        for (suffix in listOf("", "/$id", "/$id/payments")) {
            payrollError(get(p.admin, "$path$suffix?scope=SELF"), 403, "access_denied")
            assertEquals(400, get(p.owner.client, "$path$suffix?scope=unsupported").statusCode())
        }
    }
}
