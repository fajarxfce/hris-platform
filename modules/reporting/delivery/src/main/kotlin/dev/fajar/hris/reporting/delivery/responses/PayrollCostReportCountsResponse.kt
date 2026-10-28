package dev.fajar.hris.reporting.delivery.responses

data class PayrollCostReportCountsResponse(
    val assessments: Long,
    val employments: Long,
    val revisedAssessments: Long,
    val employerCost: String,
    val taxableGross: String,
    val employeeDeductions: String,
    val withheld: String,
    val takeHome: String,
)
