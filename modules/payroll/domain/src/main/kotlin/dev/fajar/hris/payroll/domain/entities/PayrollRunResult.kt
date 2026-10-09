package dev.fajar.hris.payroll.domain.entities

import dev.fajar.hris.core.domain.*
import java.time.*

data class PayrollRunResult(
    val item: PayrollRunItem,
    val facts: PayrollCalculationFacts?,
    val calculation: PayrollMonthlyCalculation?,
)
