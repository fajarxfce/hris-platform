package dev.fajar.hris.payroll.data.models

import java.time.LocalDate

data class TaxRegistrationData(
    val residency: String,
    val ptkp: String,
    val residenceCountry: String,
    val subjectiveFrom: LocalDate?,
    val subjectiveUntil: LocalDate?,
    val verifiedOn: LocalDate,
    val verificationReference: String,
)
