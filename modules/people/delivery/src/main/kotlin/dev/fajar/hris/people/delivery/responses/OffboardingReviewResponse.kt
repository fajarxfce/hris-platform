package dev.fajar.hris.people.delivery.responses

import java.time.LocalDate

data class OffboardingReviewResponse(
    val case: LifecycleCaseResponse,
    val employmentVersion: Long,
    val today: LocalDate,
)
