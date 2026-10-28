package dev.fajar.hris.reporting.domain.entities

import java.math.BigDecimal

data class PayrollCostReportCounts(
    val assessments: Long = 0,
    val employments: Long = 0,
    val revisedAssessments: Long = 0,
    val employerCost: BigDecimal = BigDecimal("0.00"),
    val taxableGross: BigDecimal = BigDecimal("0.00"),
    val employeeDeductions: BigDecimal = BigDecimal("0.00"),
    val withheld: BigDecimal = BigDecimal("0.00"),
    val takeHome: BigDecimal = BigDecimal("0.00"),
)
