package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal

data class CompensationTerms(
    val basicSalary: BigDecimal,
    val fixedEarnings: List<FixedEarning>,
    val treatment: TaxTreatment,
    val tax: TaxRegistration,
    val insuranceWage: BigDecimal,
    val insurancePrograms: Set<InsuranceProgram>,
    val insuranceExemptionReason: String?,
    val accidentRisk: AccidentRisk,
    val additionalHealthDependents: Int,
    val additionalRetirementContribution: BigDecimal,
    val qualifiedDonation: BigDecimal,
    val otherNetDeduction: BigDecimal,
)
