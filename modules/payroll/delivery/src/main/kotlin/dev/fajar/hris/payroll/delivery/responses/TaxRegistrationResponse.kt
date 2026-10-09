package dev.fajar.hris.payroll.delivery.responses

import java.time.LocalDate

data class TaxRegistrationResponse(
    val residency: String,
    val ptkp: String,
    val residenceCountry: String,
    val subjectiveFrom: LocalDate?,
    val subjectiveUntil: LocalDate?,
    val verifiedOn: LocalDate,
    val verificationReference: String,
)
