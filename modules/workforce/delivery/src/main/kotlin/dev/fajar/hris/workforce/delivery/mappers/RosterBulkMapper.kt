package dev.fajar.hris.workforce.delivery.mappers

import dev.fajar.hris.workforce.delivery.requests.RosterBulkReviewRequest
import dev.fajar.hris.workforce.delivery.responses.*
import dev.fajar.hris.workforce.domain.entities.RosterBulkReview
import dev.fajar.hris.workforce.domain.entities.RosterBulkSelection

fun RosterBulkReviewRequest.toSelection(): RosterBulkSelection =
    RosterBulkSelection(employeeIds, dates, mode, shift?.toReference())

fun RosterBulkReview.toResponse(): RosterBulkReviewResponse =
    RosterBulkReviewResponse(
        selection.employeeIds,
        selection.dates,
        selection.mode.name,
        selection.shift?.id,
        selection.shift?.version,
        employees.map { entry ->
            RosterBulkEmployeeResponse(
                entry.employee.toResponse(),
                entry.days.map { day ->
                    RosterBulkDayResponse(
                        day.workDate,
                        day.expectedVersion,
                        day.overrideActive,
                        day.before.toResponse(),
                        day.after.toResponse(),
                        day.changed,
                    )
                },
            )
        },
        revision,
        changeCount,
    )
