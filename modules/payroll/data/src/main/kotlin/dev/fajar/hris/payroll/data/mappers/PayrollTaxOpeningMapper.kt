package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.payroll.domain.entities.*
import java.math.BigDecimal
import tools.jackson.databind.ObjectMapper

fun IncomeTaxHistory.toData() =
    IncomeTaxHistoryData(
        taxableGross.toPlainString(),
        retirementContributions.toPlainString(),
        qualifiedDonations.toPlainString(),
        withheld.toPlainString(),
        employmentMonths,
        previousEmployerNet.toPlainString(),
        previousEmployerWithheld.toPlainString(),
    )

fun IncomeTaxHistoryData.toHistory() =
    IncomeTaxHistory(
        BigDecimal(taxableGross),
        BigDecimal(retirementContributions),
        BigDecimal(qualifiedDonations),
        BigDecimal(withheld),
        employmentMonths,
        BigDecimal(previousEmployerNet),
        BigDecimal(previousEmployerWithheld),
    )

fun PayrollTaxOpeningTerms.toData() =
    PayrollTaxOpeningTermsData(throughMonth, residency.name, ptkp.name, history.toData(), reference)

fun PayrollTaxOpeningTermsData.toTerms() =
    PayrollTaxOpeningTerms(
        throughMonth,
        TaxResidency.valueOf(residency),
        PtkpStatus.valueOf(ptkp),
        history.toHistory(),
        reference,
    )

fun PayrollTaxOpeningRow.toOpening(json: ObjectMapper): PayrollTaxOpening {
    val r = revision
    val terms = json.readValue(r.terms.data(), PayrollTaxOpeningTermsData::class.java).toTerms()
    return PayrollTaxOpening(
        r.openingId,
        employeeId,
        year,
        r.revision,
        terms,
        PayrollTaxOpeningStatus.valueOf(r.status),
        r.preparedBy,
        r.verifiedBy,
        r.recordedAt.toInstant(),
        r.reason,
    )
}
