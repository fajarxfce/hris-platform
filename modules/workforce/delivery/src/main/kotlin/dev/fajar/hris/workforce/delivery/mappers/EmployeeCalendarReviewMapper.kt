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
        assignmentAtStart?.let { assignment ->
            ScheduleAssignmentResponse(
                assignment.effectiveFrom,
                assignment.revision,
                assignment.days.mapKeys { it.key.name }.mapValues { it.value.toResponse() },
            )
        },
    )
