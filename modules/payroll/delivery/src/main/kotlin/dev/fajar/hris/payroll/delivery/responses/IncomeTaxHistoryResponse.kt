package dev.fajar.hris.payroll.delivery.responses

data class IncomeTaxHistoryResponse(
    val taxableGross: String,
    val retirementContributions: String,
    val qualifiedDonations: String,
    val withheld: String,
    val employmentMonths: Int,
    val previousEmployerNet: String,
    val previousEmployerWithheld: String,
)
