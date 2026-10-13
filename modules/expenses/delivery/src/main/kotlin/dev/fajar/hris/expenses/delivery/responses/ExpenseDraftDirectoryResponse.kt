package dev.fajar.hris.expenses.delivery.responses

import dev.fajar.hris.core.domain.Page
import java.time.LocalDate

data class ExpenseDraftDirectoryResponse(
    val companyDate: LocalDate,
    val timezone: String,
    val employees: Page<ExpenseDraftEmployeeResponse>,
)
