package dev.fajar.hris.expenses.domain.entities

import dev.fajar.hris.core.domain.Page
import java.time.LocalDate
import java.util.UUID

data class SelfExpenseDirectory(
    val accountId: UUID,
    val companyId: UUID,
    val asOf: LocalDate,
    val timezone: String,
    val employments: Page<ExpenseEmployment>,
)
