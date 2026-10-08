package dev.fajar.hris.workforce.delivery.mappers

import dev.fajar.hris.workforce.delivery.requests.AttendanceCaptureRequest
import dev.fajar.hris.workforce.domain.entities.*
import java.util.UUID

fun AttendanceCaptureRequest.toCapture(employeeId: UUID): AttendanceCapture =
    AttendanceCapture(
        id,
        employeeId,
        workDate,
        kind,
        capturedAt,
        deviceId,
        windowId,
        offline,
        location?.let {
            AttendanceLocation(it.latitude, it.longitude, it.accuracyMeters, it.mocked)
        },
    )
