package dev.fajar.hris.leave.delivery.requests

import java.time.YearMonth
import java.util.UUID

data class LeaveAccrualBatchRequest(
    val id: UUID,
    val typeId: UUID,
    val month: YearMonth,
    val expectedPolicyVersion: Long,
    val employeeIds: Set<UUID>? = null,
    val reason: String,
)
