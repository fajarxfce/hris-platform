package dev.fajar.hris.leave.delivery.responses

import dev.fajar.hris.core.domain.Page
import java.time.LocalDate

data class LeavePreparationDirectoryResponse(
    val companyDate: LocalDate,
    val timezone: String,
    val employees: Page<LeavePreparationEmployeeResponse>,
)
