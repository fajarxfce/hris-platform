package dev.fajar.hris.leave.domain.entities

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Current preparation facts; only submission can reserve a balance or create an approval. */
data class LeaveRequestPreview(
    val employeeId: UUID,
    val evaluatedAt: Instant,
    val timezone: String,
    val policy: LeavePolicySnapshot,
    val days: List<LeaveDay>,
    val excludedDates: List<LocalDate>,
    val balances: List<LeavePreviewBalance>,
)
