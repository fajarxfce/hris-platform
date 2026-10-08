package dev.fajar.hris.workforce.data.mappers

import dev.fajar.hris.schema.tables.records.*
import dev.fajar.hris.workforce.data.models.*
import dev.fajar.hris.workforce.domain.entities.*
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

fun AttendanceCaptureWindowsRecord.toWindow(): AttendanceCaptureWindow =
    AttendanceCaptureWindow(
        id,
        employmentId,
        accountId,
        deviceId,
        issuedAt.toInstant(),
        expiresAt.toInstant(),
        consumedBy,
    )

fun AttendanceCaptureWindow.toRow(company: UUID): AttendanceCaptureWindowsRecord =
    AttendanceCaptureWindowsRecord().also {
        it.companyId = company
        it.id = id
        it.employmentId = employeeId
        it.accountId = accountId
        it.deviceId = deviceId
        it.issuedAt = issuedAt.atOffset(ZoneOffset.UTC)
        it.expiresAt = expiresAt.atOffset(ZoneOffset.UTC)
        it.consumedBy = consumedBy
    }

fun AttendanceRow.toEntry(json: ObjectMapper): AttendanceEntry {
    val location =
        event.latitude?.let {
            AttendanceLocation(
                it,
                requireNotNull(event.longitude),
                requireNotNull(event.accuracyMeters),
                event.mocked,
            )
        }
    val capture =
        AttendanceCapture(
            event.id,
            event.employmentId,
            event.workDate,
            AttendanceKind.valueOf(event.kind),
            event.capturedAt.toInstant(),
            event.deviceId,
            event.windowId,
            event.offline,
            location,
        )
    val decision =
        review?.let {
            AttendanceReview(
                it.actorId,
                AttendanceReviewDecision.valueOf(it.decision),
                it.reviewedAt.toInstant(),
                it.reason,
            )
        }
    val initial =
        AttendanceAssessment(
            AttendanceStatus.valueOf(event.initialStatus),
            event.issues.map { AttendanceIssue.valueOf(it) }.toSet(),
            event.closingJobId,
        )
    val status =
        when (decision?.decision) {
            AttendanceReviewDecision.ACCEPT -> AttendanceStatus.ACCEPTED
            AttendanceReviewDecision.REJECT -> AttendanceStatus.REJECTED
            null -> initial.status
        }
    return AttendanceEntry(
        capture,
        event.accountId,
        event.receivedAt.toInstant(),
        json.readValue(event.schedule.data(), ScheduledDayData::class.java).toDay(),
        initial,
        status,
        decision,
        if (decision == null) 0 else 1,
    )
}

fun AttendanceEntry.toRow(company: UUID, json: ObjectMapper): AttendanceEventsRecord =
    AttendanceEventsRecord().also {
        it.companyId = company
        it.id = capture.id
        it.employmentId = capture.employeeId
        it.accountId = accountId
        it.workDate = capture.workDate
        it.kind = capture.kind.name
        it.capturedAt = capture.capturedAt.atOffset(ZoneOffset.UTC)
        it.receivedAt = receivedAt.atOffset(ZoneOffset.UTC)
        it.deviceId = capture.deviceId
        it.windowId = capture.windowId
        it.offline = capture.offline
        it.latitude = capture.location?.latitude
        it.longitude = capture.location?.longitude
        it.accuracyMeters = capture.location?.accuracyMeters
        it.mocked = capture.location?.mocked ?: false
        it.schedule = JSONB.valueOf(json.writeValueAsString(schedule.toData()))
        it.closingJobId = initial.closingJobId
        it.initialStatus = initial.status.name
        it.issues = initial.issues.map { issue -> issue.name }.sorted().toTypedArray()
    }
