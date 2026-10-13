package dev.fajar.hris.expenses.domain.entities

import dev.fajar.hris.core.domain.Page
import java.time.LocalDate

data class ExpenseDraftDirectory(
    val companyDate: LocalDate,
    val timezone: String,
    val employees: Page<ExpenseDraftEmployee>,
)
