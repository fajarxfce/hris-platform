package dev.fajar.hris.leave.delivery.responses

import java.util.UUID

data class LeaveWorkflowResponse(
    val id: UUID,
    val authorId: UUID,
    val requesterId: UUID?,
    val status: String,
    val currentStep: Int,
    val stages: List<Set<UUID>>,
    val version: Long,
)
