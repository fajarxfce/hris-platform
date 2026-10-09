package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.*

fun TaxRegistration.toResponse() =
    TaxRegistrationResponse(
        residency.name,
        ptkp.name,
        residenceCountry,
        subjectiveFrom,
        subjectiveUntil,
        verifiedOn,
        verificationReference,
    )

fun CompensationTerms.toResponse() =
    CompensationTermsResponse(
        basicSalary.toPlainString(),
        fixedEarnings.map {
            FixedEarningResponse(it.code, it.name, it.amount.toPlainString(), it.taxable)
        },
        treatment.name,
        tax.toResponse(),
        insuranceWage.toPlainString(),
        insurancePrograms.map { it.name }.toSet(),
        insuranceExemptionReason,
        accidentRisk.name,
        additionalHealthDependents,
        additionalRetirementContribution.toPlainString(),
        qualifiedDonation.toPlainString(),
        otherNetDeduction.toPlainString(),
    )

fun EmployeeCompensation.toResponse() =
    CompensationResponse(
        employeeId,
        employeeNumber,
        employeeName,
        version,
        appliedRevision,
        effectiveFrom,
        "IDR",
        terms.toResponse(),
    )

fun CompensationRevision.toResponse() =
    CompensationRevisionResponse(compensation.toResponse(), actorId, reason, recordedAt)
