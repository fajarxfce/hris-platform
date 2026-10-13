package dev.fajar.hris.expenses.domain.entities

import java.util.UUID

data class ExpenseDraftReferenceQuery(
    val categoryIds: Set<UUID> = emptySet(),
    val costCenterIds: Set<UUID> = emptySet(),
    val receiptRevisionIds: Set<UUID> = emptySet(),
)
