package dev.fajar.hris.workforce.domain.entities

import java.time.LocalDate
import java.time.YearMonth

data class WorkPeriodOverview(
    val month: YearMonth,
    val companyDate: LocalDate,
    val companyTimezone: String,
    val state: WorkPeriodState?,
    val availableActions: List<String>,
)
