package dev.fajar.hris.payroll.delivery.requests

import dev.fajar.hris.payroll.domain.entities.*
import java.time.LocalDate

data class TaxRegistrationRequest(
    val residency: TaxResidency,
    val ptkp: PtkpStatus,
    val residenceCountry: String,
    val subjectiveFrom: LocalDate? = null,
    val subjectiveUntil: LocalDate? = null,
    val verifiedOn: LocalDate,
    val verificationReference: String,
)
