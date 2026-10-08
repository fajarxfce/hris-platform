package dev.fajar.hris.workforce.delivery.requests

import java.time.Instant
import java.time.LocalDate

data class AttendanceCorrectionRequest(
    val workDate: LocalDate,
    val clockIn: Instant? = null,
    val clockOut: Instant? = null,
    val breakMinutes: Int = 0,
    val expectedVersion: Long? = null,
    val reason: String,
)
