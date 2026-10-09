package dev.fajar.hris

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PayrollPayBasisHttpTest : PayrollApiFixture() {
    private fun basis(changes: Map<String, Any?> = emptyMap()): Map<String, Any?> =
        mapOf(
            "overtimeRuleId" to "ID-PP35-2021-v1",
            "holidayAllowanceRuleId" to "ID-PERMENAKER6-2016-v1",
            "proration" to "CALENDAR_DAYS",
            "workWeek" to "FIVE_DAYS",
            "shortestWorkDay" to null,
            "overtimeEligibility" to "ELIGIBLE",
            "overtimeExemptionReference" to null,
            "regularNonFixedWage" to "500000",
            "serviceMonthConvention" to "CALENDAR_FRACTION",
            "rounding" to "HALF_UP",
            "reviewReference" to "Employment terms review",
        ) + changes

    @Test
    fun legacyCompensationAndNewPayBasisRetainHistoryAndExactOperationReplay() {
        val f = payrollFixture()
        val key = UUID.randomUUID()
        val legacy = compensationBody()
        val before = payrollBody(compensation(f, legacy, key))
        val base = "/api/v1/companies/${f.company}/payroll/employees/${f.employee}/compensation"
        assertTrue(
            payrollBody(get(f.operator.client, "$base?asOf=2026-01"))["terms"]["payBasis"].isNull
        )
        val next =
            compensationBody(0, mapOf("effectiveFrom" to "2026-07"), mapOf("payBasis" to basis()))
        payrollBody(compensation(f, next))
        val earlier = payrollBody(get(f.operator.client, "$base?asOf=2026-06"))
        assertTrue(earlier["terms"]["payBasis"].isNull)
        val current = payrollBody(get(f.operator.client, "$base?asOf=2026-07"))
        assertEquals("CALENDAR_DAYS", current["terms"]["payBasis"]["proration"].asString())
        assertEquals("500000", current["terms"]["payBasis"]["regularNonFixedWage"].asString())
        assertEquals(before, payrollBody(compensation(f, legacy, key)))
        val third =
            compensationBody(
                1,
                mapOf("effectiveFrom" to "2026-08"),
                mapOf(
                    "payBasis" to
                        basis(
                            mapOf(
                                "workWeek" to "SIX_DAYS",
                                "shortestWorkDay" to "SATURDAY",
                                "rounding" to "UP",
                            )
                        )
                ),
            )
        payrollBody(compensation(f, third))
        assertEquals(
            "FIVE_DAYS",
            payrollBody(get(f.operator.client, "$base?asOf=2026-07"))["terms"]["payBasis"][
                    "workWeek"]
                .asString(),
        )
        assertEquals(
            "SATURDAY",
            payrollBody(get(f.operator.client, "$base?asOf=2026-08"))["terms"]["payBasis"][
                    "shortestWorkDay"]
                .asString(),
        )
        assertEquals(3, count(f.company, "employee_compensation_revisions"))
    }

    @Test
    fun payBasisChangesParticipateInIdempotencyAndKeepSalaryAccessIndependent() {
        val f = payrollFixture()
        val key = UUID.randomUUID()
        val original = compensationBody(termChanges = mapOf("payBasis" to basis()))
        val first = payrollBody(compensation(f, original, key))
        assertEquals(first, payrollBody(compensation(f, original, key)))
        val changed =
            compensationBody(termChanges = mapOf("payBasis" to basis(mapOf("rounding" to "UP"))))
        payrollError(compensation(f, changed, key), 409, "operation_payload_mismatch")
        assertEquals(1, count(f.company, "employee_compensation_revisions"))
        val path =
            "/api/v1/companies/${f.company}/payroll/employees/${f.employee}/compensation?asOf=2026-01"
        payrollError(get(f.owner.client, path), 403, "access_denied")
        val manager = payrollMember(f.company, setOf("company.read", "payroll.policy.manage"))
        payrollError(get(manager.client, path), 403, "access_denied")
    }

    @Test
    fun invalidWorkWeekOrExemptionConfigurationCannotCreateACompensationRevision() {
        val f = payrollFixture()
        val key = UUID.randomUUID()
        for (change in
            listOf(
                mapOf("overtimeRuleId" to "unknown"),
                mapOf("holidayAllowanceRuleId" to "unknown"),
                mapOf("reviewReference" to ""),
                mapOf("workWeek" to "SIX_DAYS"),
                mapOf("shortestWorkDay" to "SATURDAY"),
                mapOf("overtimeEligibility" to "EXEMPT"),
                mapOf("overtimeExemptionReference" to "Role label alone"),
                mapOf("regularNonFixedWage" to "-1"),
            )) {
            payrollError(
                compensation(
                    f,
                    compensationBody(termChanges = mapOf("payBasis" to basis(change))),
                    key,
                ),
                422,
                "invalid_payroll_pay_basis",
            )
        }
        assertEquals(0, count(f.company, "employee_compensation_revisions"))
        payrollBody(
            compensation(
                f,
                compensationBody(
                    termChanges =
                        mapOf(
                            "payBasis" to
                                basis(
                                    mapOf(
                                        "overtimeEligibility" to "EXEMPT",
                                        "overtimeExemptionReference" to "Signed agreement clause 8",
                                    )
                                )
                        )
                ),
                key,
            )
        )
        val view =
            payrollBody(
                get(
                    f.operator.client,
                    "/api/v1/companies/${f.company}/payroll/employees/${f.employee}/compensation?asOf=2026-01",
                )
            )
        assertEquals("EXEMPT", view["terms"]["payBasis"]["overtimeEligibility"].asString())
    }
}
