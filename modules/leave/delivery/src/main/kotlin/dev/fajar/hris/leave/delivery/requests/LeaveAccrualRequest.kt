package dev.fajar.hris.leave.delivery.requests

import java.time.YearMonth
import java.util.UUID

data class LeaveAccrualRequest(
    val id: UUID,
    val month: YearMonth,
    val expectedEmploymentVersion: Long,
    val expectedPolicyVersion: Long,
    val expectedBalanceVersion: Long,
    val reason: String,
)
