package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.*

fun PayrollTaxOpeningEmployee.toResponse() =
    PayrollTaxOpeningEmployeeResponse(
        employee.id,
        employee.employeeNumber,
        employee.name,
        employee.employmentVersion,
        openingVersion,
        openingStatus?.name,
    )

fun PayrollTaxOpeningReview.toResponse() =
    PayrollTaxOpeningReviewResponse(
        year,
        employee.toResponse(),
        current?.toResponse(),
        Page(history.items.map { it.toSummaryResponse() }, history.nextCursor),
        frozen,
        availableActions.map { it.name }.sorted(),
    )

fun PayrollTaxOpening.toSummaryResponse() =
    PayrollTaxOpeningSummaryResponse(
        id,
        employeeId,
        year,
        version,
        status.name,
        preparedBy,
        verifiedBy,
        recordedAt,
        reason,
    )
