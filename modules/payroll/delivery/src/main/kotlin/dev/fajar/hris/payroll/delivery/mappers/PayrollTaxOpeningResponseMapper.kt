package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.*

fun IncomeTaxHistory.toResponse() =
    IncomeTaxHistoryResponse(
        taxableGross.toPlainString(),
        retirementContributions.toPlainString(),
        qualifiedDonations.toPlainString(),
        withheld.toPlainString(),
        employmentMonths,
        previousEmployerNet.toPlainString(),
        previousEmployerWithheld.toPlainString(),
    )

fun PayrollTaxOpeningTerms.toResponse() =
    PayrollTaxOpeningTermsResponse(
        throughMonth,
        residency.name,
        ptkp.name,
        history.toResponse(),
        reference,
    )

fun PayrollTaxOpening.toResponse() =
    PayrollTaxOpeningResponse(
        id,
        employeeId,
        year,
        version,
        terms.toResponse(),
        status.name,
        preparedBy,
        verifiedBy,
        recordedAt,
        reason,
    )
