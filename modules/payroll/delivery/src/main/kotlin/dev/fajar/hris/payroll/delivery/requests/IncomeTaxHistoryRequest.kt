package dev.fajar.hris.payroll.delivery.requests

data class IncomeTaxHistoryRequest(
    val taxableGross: String,
    val retirementContributions: String,
    val qualifiedDonations: String,
    val withheld: String,
    val employmentMonths: Int,
    val previousEmployerNet: String,
    val previousEmployerWithheld: String,
)
