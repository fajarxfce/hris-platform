package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.core.http.decimalAmount
import dev.fajar.hris.payroll.delivery.requests.*
import dev.fajar.hris.payroll.domain.entities.*

fun IncomeTaxHistoryRequest.toHistory() =
    IncomeTaxHistory(
        decimalAmount(taxableGross),
        decimalAmount(retirementContributions),
        decimalAmount(qualifiedDonations),
        decimalAmount(withheld),
        employmentMonths,
        decimalAmount(previousEmployerNet),
        decimalAmount(previousEmployerWithheld),
    )

fun PayrollTaxOpeningTermsRequest.toTerms() =
    PayrollTaxOpeningTerms(throughMonth, residency, ptkp, history.toHistory(), reference)
