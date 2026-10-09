package dev.fajar.hris.leave.delivery.requests

import java.util.UUID

data class LeaveYearCloseBatchRequest(
    val id: UUID,
    val typeId: UUID,
    val year: Int,
    val expectedPolicyVersion: Long,
    val employeeIds: Set<UUID>? = null,
    val reason: String,
)
