package dev.fajar.hris.reporting.delivery.responses

import java.time.LocalDate

data class AttendanceExceptionDayResponse(
    val date: LocalDate,
    val counts: AttendanceExceptionCountsResponse,
)
