package dev.fajar.hris.payroll.domain.entities

data class PayrollTaxOpeningTerms(
    val throughMonth: Int,
    val residency: TaxResidency,
    val ptkp: PtkpStatus,
    val history: IncomeTaxHistory,
    val reference: String,
)
