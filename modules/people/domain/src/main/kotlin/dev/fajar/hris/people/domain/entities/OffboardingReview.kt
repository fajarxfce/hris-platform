package dev.fajar.hris.people.domain.entities

import java.time.LocalDate

/** A current command context, not a reservation or authorization to complete offboarding. */
data class OffboardingReview(
    val case: LifecycleCaseDetails,
    val employmentVersion: Long,
    val today: LocalDate,
)
