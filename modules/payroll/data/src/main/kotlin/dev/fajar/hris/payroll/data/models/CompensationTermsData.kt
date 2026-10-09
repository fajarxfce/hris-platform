package dev.fajar.hris.payroll.data.models

data class CompensationTermsData(
    val basicSalary: String,
    val fixedEarnings: List<FixedEarningData>,
    val treatment: String,
    val tax: TaxRegistrationData,
    val insuranceWage: String,
    val insurancePrograms: Set<String>,
    val insuranceExemptionReason: String?,
    val accidentRisk: String,
    val additionalHealthDependents: Int,
    val additionalRetirementContribution: String,
    val qualifiedDonation: String,
    val otherNetDeduction: String,
)
