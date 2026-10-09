package dev.fajar.hris.payroll.delivery.requests

import dev.fajar.hris.payroll.domain.entities.*

data class PayrollTaxOpeningTermsRequest(
    val throughMonth: Int,
    val residency: TaxResidency,
    val ptkp: PtkpStatus,
    val history: IncomeTaxHistoryRequest,
    val reference: String,
)
