package dev.fajar.hris.people.domain.entities

import java.time.Instant
import java.time.LocalDate

data class OffboardingWindow(
    val companyTimezone: String,
    val today: LocalDate,
    val scheduledFor: Instant,
    val validUntil: Instant,
)
