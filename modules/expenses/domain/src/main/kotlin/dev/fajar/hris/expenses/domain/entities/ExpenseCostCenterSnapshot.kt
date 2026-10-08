package dev.fajar.hris.expenses.domain.entities

import java.util.UUID

data class ExpenseCostCenterSnapshot(
    val id: UUID,
    val code: String,
    val name: String,
    val version: Long,
)
