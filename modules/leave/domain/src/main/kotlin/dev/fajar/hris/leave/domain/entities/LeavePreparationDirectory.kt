package dev.fajar.hris.leave.domain.entities

import dev.fajar.hris.core.domain.Page
import java.time.LocalDate

data class LeavePreparationDirectory(
    val companyDate: LocalDate,
    val timezone: String,
    val employees: Page<LeavePreparationEmployee>,
)
