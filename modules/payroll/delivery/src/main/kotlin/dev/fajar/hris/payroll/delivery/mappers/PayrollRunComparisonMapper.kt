package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.*

private fun PayrollComparisonAmounts.response() =
    PayrollComparisonAmountsResponse(
        taxableGross.setScale(2).toPlainString(),
        withheld.setScale(2).toPlainString(),
        takeHome.setScale(2).toPlainString(),
    )

private fun PayrollRun.comparisonResponse() =
    PayrollComparisonRunResponse(
        id,
        periodId,
        number,
        earningsMonth,
        status.name,
        version,
        totalEmployees,
    )

private fun PayrollComparisonEmployee.response() =
    PayrollComparisonEmployeeResponse(number, name, amounts.response())

fun PayrollRunComparison.toResponse(): PayrollRunComparisonResponse {
    val summary = data.summary
    return PayrollRunComparisonResponse(
        baseline.comparisonResponse(),
        current.comparisonResponse(),
        PayrollComparisonSummaryResponse(
            summary.baselineEmployees,
            summary.currentEmployees,
            summary.matched,
            summary.added,
            summary.removed,
            summary.changed,
            summary.unchanged,
            summary.baseline.response(),
            summary.current.response(),
            summary.delta.response(),
        ),
        Page(
            data.employees.items.map {
                PayrollComparisonItemResponse(
                    it.employmentId,
                    it.baseline?.response(),
                    it.current?.response(),
                    it.change.name,
                    it.delta.response(),
                )
            },
            data.employees.nextCursor,
        ),
        changedOnly,
        observedAt,
    )
}
