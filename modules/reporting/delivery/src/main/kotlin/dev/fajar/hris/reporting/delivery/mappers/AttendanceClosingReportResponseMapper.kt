package dev.fajar.hris.reporting.delivery.mappers

import dev.fajar.hris.reporting.delivery.responses.*
import dev.fajar.hris.reporting.domain.entities.*

fun AttendanceClosingFacts.toResponse() =
    AttendanceClosingFactsResponse(
        workedDays,
        absenceDays,
        unrecordedDays,
        offDays,
        unassignedDays,
        acceptedMinutes,
    )

fun AttendanceClosingCounts.toResponse() =
    AttendanceClosingCountsResponse(
        closedMonths,
        employmentMonths,
        employments,
        adjustedEmploymentMonths,
        days,
        scheduledDays,
        scheduledMinutes,
        original.toResponse(),
        current.toResponse(),
    )

fun AttendanceClosingReference.toResponse() =
    AttendanceClosingReferenceResponse(periodId, jobId, version, closedAt, timezone)
