package dev.fajar.hris.people.domain.entities

import dev.fajar.hris.core.domain.Page
import java.time.LocalDate

data class SelfEmploymentDirectory(
    val asOf: LocalDate,
    val timezone: String,
    val employments: Page<Employee>,
)
