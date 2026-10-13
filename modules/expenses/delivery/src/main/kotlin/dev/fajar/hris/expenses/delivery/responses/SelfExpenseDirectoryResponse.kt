package dev.fajar.hris.expenses.delivery.responses

import dev.fajar.hris.core.domain.Page
import java.time.LocalDate
import java.util.UUID

data class SelfExpenseDirectoryResponse(
    val accountId: UUID,
    val companyId: UUID,
    val asOf: LocalDate,
    val timezone: String,
    val employments: Page<ExpenseEmploymentResponse>,
)
