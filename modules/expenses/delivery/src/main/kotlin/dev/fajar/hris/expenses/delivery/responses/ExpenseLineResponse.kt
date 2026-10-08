package dev.fajar.hris.expenses.delivery.responses

import java.time.LocalDate
import java.util.UUID

data class ExpenseLineResponse(
    val id: UUID,
    val categoryId: UUID,
    val occurredOn: LocalDate,
    val amount: String,
    val description: String,
    val costCenterId: UUID?,
    val receiptRevisionIds: List<UUID>,
)
