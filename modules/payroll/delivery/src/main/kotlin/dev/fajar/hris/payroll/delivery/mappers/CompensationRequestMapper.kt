package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.core.http.decimalAmount
import dev.fajar.hris.payroll.delivery.requests.*
import dev.fajar.hris.payroll.domain.entities.*

fun TaxRegistrationRequest.toRegistration() =
    TaxRegistration(
        residency,
        ptkp,
        residenceCountry,
        subjectiveFrom,
        subjectiveUntil,
        verifiedOn,
        verificationReference,
    )

fun CompensationTermsRequest.toTerms() =
    CompensationTerms(
        decimalAmount(basicSalary),
        java.util.List.copyOf(
            fixedEarnings.map {
                FixedEarning(it.code, it.name, decimalAmount(it.amount), it.taxable)
            }
        ),
        treatment,
        tax.toRegistration(),
        decimalAmount(insuranceWage),
        java.util.Set.copyOf(insurancePrograms),
        insuranceExemptionReason,
        accidentRisk,
        additionalHealthDependents,
        decimalAmount(additionalRetirementContribution),
        decimalAmount(qualifiedDonation),
        decimalAmount(otherNetDeduction),
        payBasis?.toBasis(),
    )
