package dev.fajar.hris.people.delivery.responses

import dev.fajar.hris.core.domain.Page
import java.time.LocalDate

data class SelfEmploymentDirectoryResponse(
    val asOf: LocalDate,
    val timezone: String,
    val employments: Page<EmployeeResponse>,
)
