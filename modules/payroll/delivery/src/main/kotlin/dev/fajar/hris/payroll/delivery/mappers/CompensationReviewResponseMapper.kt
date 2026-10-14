package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.payroll.delivery.responses.CompensationEmployeeResponse
import dev.fajar.hris.payroll.delivery.responses.CompensationReviewResponse
import dev.fajar.hris.payroll.domain.entities.CompensationEmployee
import dev.fajar.hris.payroll.domain.entities.CompensationReview

fun CompensationEmployee.toResponse() =
    CompensationEmployeeResponse(id, employeeNumber, name, employmentVersion)

fun CompensationReview.toResponse() =
    CompensationReviewResponse(
        asOf,
        employee.toResponse(),
        current?.toResponse(),
        effective?.toResponse(),
        Page(history.items.map { it.toResponse() }, history.nextCursor),
        canChange,
    )
