package dev.fajar.hris.leave.delivery.responses

import dev.fajar.hris.core.domain.Page

data class LeaveCarryExpiryDetailsResponse(
    val expiry: LeaveCarryExpiryResponse,
    val attempts: List<LeaveCarryExpiryAttemptResponse>,
    val job: LeaveCarryExpiryJobResponse,
    val results: Page<LeaveCarryExpiryOutcomeResponse>,
    val employees: List<LeaveEmployeeReferenceResponse>,
    val availableActions: Set<String>,
)
