package dev.fajar.hris.people.delivery.responses

import dev.fajar.hris.core.domain.Page
import java.time.LocalDate

data class CompanyDirectoryResponse(
    val asOf: LocalDate,
    val timezone: String,
    val employees: Page<DirectoryEmployeeResponse>,
)
