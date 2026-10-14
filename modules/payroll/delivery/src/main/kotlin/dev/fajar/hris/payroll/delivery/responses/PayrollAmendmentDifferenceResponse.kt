package dev.fajar.hris.payroll.delivery.responses

data class PayrollAmendmentDifferenceResponse(
    val taxableGross: String,
    val withheld: String,
    val takeHome: String,
    val employeeDeductions: String,
    val employerCost: String,
)
