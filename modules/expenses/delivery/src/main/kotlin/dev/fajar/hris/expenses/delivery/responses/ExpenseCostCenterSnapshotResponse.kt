package dev.fajar.hris.expenses.delivery.responses

import java.util.UUID

data class ExpenseCostCenterSnapshotResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val version: Long,
)
