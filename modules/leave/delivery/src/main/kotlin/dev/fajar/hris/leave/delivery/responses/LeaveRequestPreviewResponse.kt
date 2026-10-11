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
    @field:io.swagger.v3.oas.annotations.media.Schema(
        pattern = "[0-9a-f]{64}",
        minLength = 64,
        maxLength = 64,
    )
    val revision: String,
)
