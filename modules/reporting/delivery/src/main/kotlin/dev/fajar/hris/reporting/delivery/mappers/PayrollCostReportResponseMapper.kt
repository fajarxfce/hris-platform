package dev.fajar.hris.reporting.delivery.mappers

import dev.fajar.hris.reporting.delivery.responses.PayrollCostReportCountsResponse
import dev.fajar.hris.reporting.domain.entities.PayrollCostReportCounts

fun PayrollCostReportCounts.toResponse() =
    PayrollCostReportCountsResponse(
        assessments,
        employments,
        revisedAssessments,
        employerCost.toPlainString(),
        taxableGross.toPlainString(),
        employeeDeductions.toPlainString(),
        withheld.toPlainString(),
        takeHome.toPlainString(),
    )
