package dev.fajar.hris.workforce.delivery.responses

import java.time.LocalDate

data class AttendanceDayResponse(
    val workDate: LocalDate,
    val entries: List<AttendanceEntryResponse>,
    val acceptedMinutes: Long,
    val pendingCount: Int,
    val incomplete: Boolean,
)
