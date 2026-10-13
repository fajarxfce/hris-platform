package dev.fajar.hris.workforce.delivery.responses

import java.time.LocalDate
import java.util.UUID

data class RosterRecordResponse(
    val id: UUID,
    val employeeId: UUID,
    val version: Long,
    val workDate: LocalDate,
    val shift: ShiftSnapshotResponse?,
)
