package dev.fajar.hris.reporting.delivery.mappers

import dev.fajar.hris.reporting.delivery.responses.*
import dev.fajar.hris.reporting.domain.entities.*

fun ExpenseReportCounts.toResponse() =
    ExpenseReportCountsResponse(claims, employments, lines, amount.toPlainString())

fun ExpenseReportClaim.toResponse() =
    ExpenseReportClaimResponse(
        id,
        submissionId,
        submissionNumber,
        employeeId,
        employeeNumber,
        employeeName,
        title,
        status.name,
        submittedAt,
        version,
        lines,
        amount.toPlainString(),
        submissionAmount.toPlainString(),
    )
