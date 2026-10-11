package dev.fajar.hris.leave.delivery.responses

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LeaveRequestPreviewResponse(
    val employeeId: UUID,
    val evaluatedAt: Instant,
    val timezone: String,
    val policy: LeavePolicySnapshotResponse,
    val days: List<LeaveDayResponse>,
    val chargedDays: String,
    val excludedDates: List<LocalDate>,
    val balances: List<LeavePreviewBalanceResponse>,
)
