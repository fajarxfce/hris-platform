package dev.fajar.hris.reporting.domain.entities

import java.time.LocalDate

data class AttendanceExceptionDay(val date: LocalDate, val counts: AttendanceExceptionCounts)
