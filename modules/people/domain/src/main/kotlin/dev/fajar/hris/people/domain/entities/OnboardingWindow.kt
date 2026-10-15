package dev.fajar.hris.people.domain.entities

import java.time.Instant
import java.time.LocalDate

data class OnboardingWindow(
    val companyTimezone: String,
    val today: LocalDate,
    val scheduledFor: Instant,
    val validUntil: Instant,
)
