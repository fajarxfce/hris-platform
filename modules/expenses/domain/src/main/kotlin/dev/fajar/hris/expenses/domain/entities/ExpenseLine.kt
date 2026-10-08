package dev.fajar.hris.expenses.domain.entities

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ExpenseLine(
    val id: UUID,
    val categoryId: UUID,
    val occurredOn: LocalDate,
    val amount: BigDecimal,
    val description: String,
    val costCenterId: UUID?,
    val receiptRevisionIds: List<UUID>,
)
