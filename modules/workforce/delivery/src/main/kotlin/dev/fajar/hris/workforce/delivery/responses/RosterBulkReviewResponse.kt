package dev.fajar.hris.workforce.delivery.responses

import java.time.LocalDate
import java.util.UUID

data class RosterBulkReviewResponse(
    val employeeIds: List<UUID>,
    val dates: List<LocalDate>,
    val mode: String,
    val shiftId: UUID?,
    val shiftVersion: Long?,
    val employees: List<RosterBulkEmployeeResponse>,
    val revision: String,
    val changeCount: Int,
)
