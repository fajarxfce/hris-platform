package dev.fajar.hris.workforce.data.mappers

import dev.fajar.hris.workforce.data.models.AttendanceExceptionRow
import dev.fajar.hris.workforce.domain.entities.AttendanceException
import dev.fajar.hris.workforce.domain.policies.attendancePairIncomplete

fun AttendanceExceptionRow.toAttendanceException(): AttendanceException {
    require(employeeNumber.isNotBlank() && employeeName.isNotBlank())
    require(pendingCount in 0..32 && acceptedClockIns in 0..32 && acceptedClockOuts in 0..32)
    require(pendingCount + acceptedClockIns + acceptedClockOuts <= 32)
    require(correctionVersion == null || correctionVersion >= 0)
    val incomplete =
        attendancePairIncomplete(
            acceptedClockIns == 1,
            acceptedClockOuts == 1,
            correctionVersion != null,
        )
    require(pendingCount > 0 || incomplete)
    return AttendanceException(
        employeeId,
        employeeNumber,
        employeeName,
        workDate,
        pendingCount,
        incomplete,
        correctionVersion,
        lastReceivedAt.toInstant(),
    )
}
