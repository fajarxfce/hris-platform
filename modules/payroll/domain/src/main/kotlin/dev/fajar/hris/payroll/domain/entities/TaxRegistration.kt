package dev.fajar.hris.payroll.domain.entities

import java.time.LocalDate

data class TaxRegistration(
    val residency: TaxResidency,
    val ptkp: PtkpStatus,
    val residenceCountry: String,
    val subjectiveFrom: LocalDate?,
    val subjectiveUntil: LocalDate?,
    val verifiedOn: LocalDate,
    val verificationReference: String,
)
