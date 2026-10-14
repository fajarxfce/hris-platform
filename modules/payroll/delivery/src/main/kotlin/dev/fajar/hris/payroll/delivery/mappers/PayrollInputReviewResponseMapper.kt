package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.PayrollInputEmployee
import dev.fajar.hris.payroll.domain.entities.PayrollInputReview

fun PayrollInputEmployee.toResponse() =
    PayrollInputEmployeeResponse(
        employee.id,
        employee.employeeNumber,
        employee.name,
        employee.employmentVersion,
        inputVersion,
        inputStatus?.name,
    )

fun PayrollInputReview.toResponse() =
    PayrollInputReviewResponse(
        earningsMonth,
        employee.toResponse(),
        source?.toResponse(),
        current?.toResponse(),
        Page(history.items.map { it.toResponse() }, history.nextCursor),
        frozen,
        availableActions.map { it.name }.sorted(),
    )
