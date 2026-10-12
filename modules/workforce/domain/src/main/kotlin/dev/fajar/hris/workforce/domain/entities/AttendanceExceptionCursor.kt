package dev.fajar.hris.workforce.domain.entities

import java.time.LocalDate
import java.util.UUID

data class AttendanceExceptionCursor(val workDate: LocalDate, val employeeId: UUID)
