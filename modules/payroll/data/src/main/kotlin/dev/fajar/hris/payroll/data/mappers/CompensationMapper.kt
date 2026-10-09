package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.payroll.domain.entities.*
import java.math.BigDecimal
import java.time.YearMonth
import tools.jackson.databind.ObjectMapper

fun TaxRegistration.toData() =
    TaxRegistrationData(
        residency.name,
        ptkp.name,
        residenceCountry,
        subjectiveFrom,
        subjectiveUntil,
        verifiedOn,
        verificationReference,
    )

fun TaxRegistrationData.toRegistration() =
    TaxRegistration(
        TaxResidency.valueOf(residency),
        PtkpStatus.valueOf(ptkp),
        residenceCountry,
        subjectiveFrom,
        subjectiveUntil,
        verifiedOn,
        verificationReference,
    )

fun CompensationTerms.toData() =
    CompensationTermsData(
        basicSalary.toPlainString(),
        fixedEarnings.map {
            FixedEarningData(it.code, it.name, it.amount.toPlainString(), it.taxable)
        },
        treatment.name,
        tax.toData(),
        insuranceWage.toPlainString(),
        insurancePrograms.map { it.name }.toSet(),
        insuranceExemptionReason,
        accidentRisk.name,
        additionalHealthDependents,
        additionalRetirementContribution.toPlainString(),
        qualifiedDonation.toPlainString(),
        otherNetDeduction.toPlainString(),
    )

fun CompensationTermsData.toTerms() =
    CompensationTerms(
        BigDecimal(basicSalary),
        java.util.List.copyOf(
            fixedEarnings.map { FixedEarning(it.code, it.name, BigDecimal(it.amount), it.taxable) }
        ),
        TaxTreatment.valueOf(treatment),
        tax.toRegistration(),
        BigDecimal(insuranceWage),
        java.util.Set.copyOf(insurancePrograms.map { InsuranceProgram.valueOf(it) }),
        insuranceExemptionReason,
        AccidentRisk.valueOf(accidentRisk),
        additionalHealthDependents,
        BigDecimal(additionalRetirementContribution),
        BigDecimal(qualifiedDonation),
        BigDecimal(otherNetDeduction),
    )

fun CompensationRow.toCompensation(json: ObjectMapper) =
    EmployeeCompensation(
        revision.employmentId,
        revision.employeeNumber,
        revision.employeeName,
        version,
        revision.revision,
        YearMonth.from(revision.effectiveFrom),
        json.readValue(revision.terms.data(), CompensationTermsData::class.java).toTerms(),
    )

fun CompensationRow.toHistory(json: ObjectMapper) =
    CompensationRevision(
        toCompensation(json),
        revision.actorId,
        revision.reason,
        revision.recordedAt.toInstant(),
    )
