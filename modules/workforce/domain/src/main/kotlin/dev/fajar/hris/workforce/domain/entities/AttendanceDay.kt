package dev.fajar.hris.workforce.domain.entities

import java.time.LocalDate

data class AttendanceDay(
    val workDate: LocalDate,
    val entries: List<AttendanceEntry>,
    val acceptedMinutes: Long,
    val pendingCount: Int,
    val incomplete: Boolean,
)
