package dev.fajar.hris.payroll.delivery.responses

data class PayrollTaxOpeningTermsResponse(
    val throughMonth: Int,
    val residency: String,
    val ptkp: String,
    val history: IncomeTaxHistoryResponse,
    val reference: String,
)
