package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.payroll.delivery.responses.CompensationReviewResponse
import dev.fajar.hris.payroll.delivery.responses.PayrollEmployeeResponse
import dev.fajar.hris.payroll.domain.entities.CompensationReview
import dev.fajar.hris.payroll.domain.entities.PayrollEmployee

fun PayrollEmployee.toResponse() =
    PayrollEmployeeResponse(id, employeeNumber, name, employmentVersion)

fun CompensationReview.toResponse() =
    CompensationReviewResponse(
        asOf,
        employee.toResponse(),
        current?.toResponse(),
        effective?.toResponse(),
        Page(history.items.map { it.toResponse() }, history.nextCursor),
        canChange,
    )
