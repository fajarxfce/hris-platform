package dev.fajar.hris.payroll.data.models

data class PayrollTaxOpeningTermsData(
    val throughMonth: Int,
    val residency: String,
    val ptkp: String,
    val history: IncomeTaxHistoryData,
    val reference: String,
)
