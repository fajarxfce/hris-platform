package dev.fajar.hris.expenses.delivery.responses

import java.util.UUID

data class ExpenseDraftLabelReferenceResponse(
    val id: UUID,
    val code: String?,
    val name: String?,
    val active: Boolean?,
)
