package dev.fajar.hris.payroll.delivery.requests

import dev.fajar.hris.payroll.domain.entities.*

data class CompensationTermsRequest(
    val basicSalary: String,
    val fixedEarnings: List<FixedEarningRequest> = emptyList(),
    val treatment: TaxTreatment,
    val tax: TaxRegistrationRequest,
    val insuranceWage: String,
    val insurancePrograms: Set<InsuranceProgram>,
    val insuranceExemptionReason: String? = null,
    val accidentRisk: AccidentRisk,
    val additionalHealthDependents: Int = 0,
    val additionalRetirementContribution: String = "0",
    val qualifiedDonation: String = "0",
    val otherNetDeduction: String = "0",
    val payBasis: PayrollPayBasisRequest? = null,
)
