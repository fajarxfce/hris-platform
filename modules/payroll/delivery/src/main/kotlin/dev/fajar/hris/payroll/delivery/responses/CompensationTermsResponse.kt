package dev.fajar.hris.payroll.delivery.responses

data class CompensationTermsResponse(
    val basicSalary: String,
    val fixedEarnings: List<FixedEarningResponse>,
    val treatment: String,
    val tax: TaxRegistrationResponse,
    val insuranceWage: String,
    val insurancePrograms: Set<String>,
    val insuranceExemptionReason: String?,
    val accidentRisk: String,
    val additionalHealthDependents: Int,
    val additionalRetirementContribution: String,
    val qualifiedDonation: String,
    val otherNetDeduction: String,
)
