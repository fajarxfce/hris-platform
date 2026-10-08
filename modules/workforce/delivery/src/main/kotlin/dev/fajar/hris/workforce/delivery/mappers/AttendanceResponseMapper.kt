package dev.fajar.hris.workforce.delivery.mappers

import dev.fajar.hris.workforce.delivery.responses.*
import dev.fajar.hris.workforce.domain.entities.*

fun AttendanceCaptureWindow.toResponse(): AttendanceWindowResponse =
    AttendanceWindowResponse(id, employeeId, deviceId, issuedAt, expiresAt)

fun AttendanceDay.toResponse(): AttendanceDayResponse =
    AttendanceDayResponse(
        workDate,
        entries.map { it.toResponse() },
        acceptedMinutes,
        pendingCount,
        incomplete,
    )

fun AttendanceEntry.toResponse(): AttendanceEntryResponse =
    AttendanceEntryResponse(
        capture.id,
        capture.kind.name,
        capture.capturedAt,
        receivedAt,
        capture.offline,
        capture.location?.let {
            AttendanceLocationResponse(it.latitude, it.longitude, it.accuracyMeters, it.mocked)
        },
        schedule.toResponse(),
        status.name,
        initial.status.name,
        initial.issues.map { it.name }.toSet(),
        review?.let {
            AttendanceReviewResponse(it.actorId, it.decision.name, it.reviewedAt, it.reason)
        },
        version,
    )
