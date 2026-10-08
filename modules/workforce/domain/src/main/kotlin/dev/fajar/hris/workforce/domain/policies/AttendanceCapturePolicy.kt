package dev.fajar.hris.workforce.domain.policies

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.domain.entities.*
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.*

fun validateAttendanceCapture(capture: AttendanceCapture, receivedAt: Instant): Result<Unit> {
    if (
        capture.capturedAt.isAfter(receivedAt.plusSeconds(30)) ||
            capture.capturedAt.isBefore(receivedAt.minus(Duration.ofDays(31))) ||
            abs(
                ChronoUnit.DAYS.between(
                    capture.workDate,
                    capture.capturedAt.atOffset(ZoneOffset.UTC).toLocalDate(),
                )
            ) > 2
    ) {
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_capture_time"))
    }
    val location = capture.location
    if (
        location != null &&
            (!location.latitude.isFinite() ||
                location.latitude !in -90.0..90.0 ||
                !location.longitude.isFinite() ||
                location.longitude !in -180.0..180.0 ||
                !location.accuracyMeters.isFinite() ||
                location.accuracyMeters !in 0.0..100000.0)
    ) {
        return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_location_evidence"))
    }
    return Result.Success(Unit)
}

fun assessAttendance(
    capture: AttendanceCapture,
    accountId: UUID,
    window: AttendanceCaptureWindow?,
    schedule: ScheduledDay,
    prior: List<AttendanceEntry>,
    receivedAt: Instant,
    corrected: Boolean = false,
): Result<AttendanceAssessment> {
    val issues = mutableSetOf<AttendanceIssue>()
    if (corrected) issues += AttendanceIssue.CORRECTED_DAY
    if (capture.offline) issues += AttendanceIssue.OFFLINE
    if (window == null) {
        if (capture.windowId != null)
            return Result.Failed(Failure(FailureKind.VALIDATION, "capture_window_unavailable"))
        issues += AttendanceIssue.UNVERIFIED_CAPTURE
    } else {
        if (
            window.id != capture.windowId ||
                window.accountId != accountId ||
                window.employeeId != capture.employeeId ||
                window.deviceId != capture.deviceId
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "capture_window_unavailable"))
        if (window.consumedBy != null)
            return Result.Failed(Failure(FailureKind.CONFLICT, "capture_window_consumed"))
        if (!receivedAt.isBefore(window.expiresAt)) issues += AttendanceIssue.WINDOW_EXPIRED
        if (
            capture.capturedAt.isBefore(window.issuedAt.minusSeconds(30)) ||
                !capture.capturedAt.isBefore(window.expiresAt)
        )
            issues += AttendanceIssue.UNVERIFIED_CAPTURE
    }
    if (schedule is ScheduledDay.Work) {
        if (
            capture.capturedAt.isBefore(schedule.startsAt.minus(Duration.ofHours(6))) ||
                capture.capturedAt.isAfter(schedule.endsAt.plus(Duration.ofHours(6)))
        )
            issues += AttendanceIssue.OUTSIDE_SHIFT_WINDOW
        val policy = schedule.shift.details
        val location = capture.location
        if (location == null && (policy.locationRequired || policy.fence != null))
            issues += AttendanceIssue.LOCATION_REQUIRED
        if (location != null) {
            if (location.mocked) issues += AttendanceIssue.MOCK_LOCATION
            if (location.accuracyMeters > policy.maxAccuracyMeters)
                issues += AttendanceIssue.LOW_ACCURACY
            val fence = policy.fence
            if (fence != null && geoDistanceMeters(location, fence) > fence.radiusMeters)
                issues += AttendanceIssue.OUTSIDE_FENCE
        }
    } else issues += AttendanceIssue.UNSCHEDULED
    if (validateAttendanceSequence(capture, prior) is Result.Failed)
        issues += AttendanceIssue.SEQUENCE_REVIEW
    return Result.Success(
        AttendanceAssessment(
            if (issues.isEmpty()) AttendanceStatus.ACCEPTED else AttendanceStatus.PENDING,
            issues.toSet(),
        )
    )
}

fun geoDistanceMeters(location: AttendanceLocation, fence: GeoFence): Double {
    val lat = Math.toRadians(fence.latitude - location.latitude)
    val lon = Math.toRadians(fence.longitude - location.longitude)
    val a =
        sin(lat / 2).pow(2) +
            cos(Math.toRadians(location.latitude)) *
                cos(Math.toRadians(fence.latitude)) *
                sin(lon / 2).pow(2)
    return 6371008.8 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
}
