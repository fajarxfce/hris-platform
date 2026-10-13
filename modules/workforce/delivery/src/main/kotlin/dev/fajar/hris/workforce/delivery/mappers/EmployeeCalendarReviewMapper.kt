package dev.fajar.hris.workforce.delivery.mappers

import dev.fajar.hris.workforce.delivery.responses.*
import dev.fajar.hris.workforce.domain.entities.EmployeeCalendarReview
import dev.fajar.hris.workforce.domain.entities.WorkforceEmployeeReference

fun WorkforceEmployeeReference.toResponse(): WorkforceEmployeeResponse =
    WorkforceEmployeeResponse(id, employeeNumber, legalName, status.name, version)

fun EmployeeCalendarReview.toResponse(): EmployeeCalendarReviewResponse =
    EmployeeCalendarReviewResponse(
        employee.toResponse(),
        from,
        until,
        calendar.toResponse(),
        assignmentAtStart?.toResponse(),
    )
