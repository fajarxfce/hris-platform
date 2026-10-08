package dev.fajar.hris.expenses.delivery.responses

import java.util.UUID

data class ExpenseApprovalResponse(
    val id: UUID,
    val status: String,
    val version: Long,
    val currentStep: Int,
    val templateId: UUID,
    val templateRevision: Long,
    val stages: List<Set<UUID>>,
)
