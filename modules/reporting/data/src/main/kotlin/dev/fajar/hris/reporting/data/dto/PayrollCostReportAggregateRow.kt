package dev.fajar.hris.reporting.data.dto

import java.math.BigDecimal
import java.time.LocalDate

data class PayrollCostReportAggregateRow(
    val month: LocalDate?,
    val assessments: Long,
    val employments: Long,
    val revisedAssessments: Long,
    val employerCost: BigDecimal,
    val taxableGross: BigDecimal,
    val employeeDeductions: BigDecimal,
    val withheld: BigDecimal,
    val takeHome: BigDecimal,
    val missingValues: Long = 0,
)
