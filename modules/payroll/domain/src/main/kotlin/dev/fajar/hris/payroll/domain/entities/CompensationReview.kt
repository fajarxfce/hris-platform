package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.core.domain.Page
import java.time.YearMonth

data class CompensationReview(
    val asOf: YearMonth,
    val employee: PayrollEmployee,
    val current: EmployeeCompensation?,
    val effective: EmployeeCompensation?,
    val history: Page<CompensationRevision>,
    val canChange: Boolean,
)
