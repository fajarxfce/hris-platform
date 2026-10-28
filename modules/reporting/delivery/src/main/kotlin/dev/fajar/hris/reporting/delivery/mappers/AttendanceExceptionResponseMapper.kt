package dev.fajar.hris.reporting.delivery.mappers

import dev.fajar.hris.reporting.delivery.responses.AttendanceExceptionCountsResponse
import dev.fajar.hris.reporting.domain.entities.AttendanceExceptionCounts

fun AttendanceExceptionCounts.toResponse() =
    AttendanceExceptionCountsResponse(
        observedDays,
        affectedDays,
        affectedEmployments,
        pendingDays,
        incompleteDays,
        pendingEvents,
    )
