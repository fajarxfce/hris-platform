package dev.fajar.hris.payroll.data.models

data class IncomeTaxHistoryData(
    val taxableGross: String,
    val retirementContributions: String,
    val qualifiedDonations: String,
    val withheld: String,
    val employmentMonths: Int,
    val previousEmployerNet: String,
    val previousEmployerWithheld: String,
)
