package dev.fajar.hris.expenses.delivery.requests

import java.time.LocalDate
import java.util.UUID

data class ExpenseLineRequest(
    val id: UUID,
    val categoryId: UUID,
    val occurredOn: LocalDate,
    val amount: String,
    val description: String,
    val costCenterId: UUID? = null,
    val receiptRevisionIds: List<UUID> = emptyList(),
)
