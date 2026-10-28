package dev.fajar.hris.reporting.data.mappers

import dev.fajar.hris.reporting.data.dto.AttendanceClosingEmployeeRow
import dev.fajar.hris.reporting.domain.entities.*
import java.time.YearMonth
import java.time.ZoneId

fun AttendanceClosingEmployeeRow.toAttendanceClosingEmployee(
    month: YearMonth
): AttendanceClosingEmployee {
    check(employeeNumber.isNotBlank() && employeeNumber.length <= 32)
    check(employeeName.isNotBlank() && employeeName.length <= 200)
    val v = report
    check(v.month == month.atDay(1) && requireNotNull(v.version) > 0)
    check(requireNotNull(v.timezone) in ZoneId.getAvailableZoneIds())
    val closing =
        AttendanceClosingReference(
            requireNotNull(v.periodId),
            requireNotNull(v.jobId),
            v.version,
            requireNotNull(v.closedAt).toInstant(),
            v.timezone,
        )
    val counts = v.toAttendanceClosingCounts()
    check(
        counts.closedMonths == 1L &&
            counts.employmentMonths == 1L &&
            counts.employments == 1L &&
            counts.days == month.lengthOfMonth().toLong()
    )
    val adjustment =
        if (adjustmentId == null) {
            check(
                adjustmentRevision == 0L &&
                    adjustmentAt == null &&
                    counts.adjustedEmploymentMonths == 0L
            )
            null
        } else {
            check(adjustmentRevision > 0 && counts.adjustedEmploymentMonths == 1L)
            val at = requireNotNull(adjustmentAt).toInstant()
            check(at >= closing.closedAt)
            AttendanceClosingAdjustmentReference(adjustmentId, adjustmentRevision, at)
        }
    return AttendanceClosingEmployee(
        employeeId,
        employeeNumber,
        employeeName,
        closing,
        adjustment,
        counts,
    )
}
