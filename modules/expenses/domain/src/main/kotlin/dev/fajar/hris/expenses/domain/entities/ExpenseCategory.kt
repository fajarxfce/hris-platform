package dev.fajar.hris.expenses.domain.entities

import java.time.LocalDate
import java.util.UUID

data class ExpenseCategory(
    val id: UUID,
    val code: String,
    val version: Long,
    val appliedRevision: Long,
    val effectiveFrom: LocalDate,
    val policy: ExpensePolicy,
    val active: Boolean,
)
