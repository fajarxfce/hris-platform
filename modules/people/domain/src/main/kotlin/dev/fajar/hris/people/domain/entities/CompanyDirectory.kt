package dev.fajar.hris.people.domain.entities

import dev.fajar.hris.core.domain.Page
import java.time.LocalDate

data class CompanyDirectory(
    val asOf: LocalDate,
    val timezone: String,
    val employees: Page<DirectoryEmployee>,
)
