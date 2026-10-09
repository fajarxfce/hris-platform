package dev.fajar.hris.workforce.delivery.responses

import java.util.UUID

data class OvertimeWorkflowResponse(
    val id: UUID,
    val status: String,
    val currentStep: Int,
    val stages: List<Set<UUID>>,
    val version: Long,
)
