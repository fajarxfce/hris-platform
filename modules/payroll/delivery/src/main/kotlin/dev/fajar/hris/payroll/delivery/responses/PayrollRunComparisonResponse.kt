package dev.fajar.hris.payroll.delivery.responses

import dev.fajar.hris.core.domain.Page
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

data class PayrollComparisonAmountsResponse(
    val taxableGross: String,
    val withheld: String,
    val takeHome: String,
)

data class PayrollComparisonRunResponse(
    val id: UUID,
    val periodId: UUID,
    val number: Int,
    val earningsMonth: YearMonth,
    val status: String,
    val version: Long,
    val employees: Int,
)

data class PayrollComparisonEmployeeResponse(
    val number: String,
    val name: String,
    val amounts: PayrollComparisonAmountsResponse,
)

data class PayrollComparisonItemResponse(
    val employmentId: UUID,
    val baseline: PayrollComparisonEmployeeResponse?,
    val current: PayrollComparisonEmployeeResponse?,
    val change: String,
    val delta: PayrollComparisonAmountsResponse,
)

data class PayrollComparisonSummaryResponse(
    val baselineEmployees: Int,
    val currentEmployees: Int,
    val matched: Int,
    val added: Int,
    val removed: Int,
    val changed: Int,
    val unchanged: Int,
    val baseline: PayrollComparisonAmountsResponse,
    val current: PayrollComparisonAmountsResponse,
    val delta: PayrollComparisonAmountsResponse,
)

data class PayrollRunComparisonResponse(
    val baseline: PayrollComparisonRunResponse,
    val current: PayrollComparisonRunResponse,
    val summary: PayrollComparisonSummaryResponse,
    val employees: Page<PayrollComparisonItemResponse>,
    val changedOnly: Boolean,
    val observedAt: Instant,
)
