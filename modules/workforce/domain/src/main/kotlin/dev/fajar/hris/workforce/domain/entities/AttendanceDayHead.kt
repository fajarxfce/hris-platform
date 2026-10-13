package dev.fajar.hris.workforce.domain.entities

import java.time.LocalDate
import java.util.UUID

/** Projection identity and version; independent of an event or correction revision. */
data class AttendanceDayHead(
    val id: UUID,
    val employeeId: UUID,
    val workDate: LocalDate,
    val version: Long,
)
