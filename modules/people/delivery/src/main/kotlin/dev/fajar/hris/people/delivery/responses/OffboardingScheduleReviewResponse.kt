package dev.fajar.hris.people.delivery.responses

import java.time.Instant
import java.time.LocalDate

data class OffboardingScheduleReviewResponse(
    val case: LifecycleCaseResponse,
    val employmentVersion: Long,
    val appliedEmploymentRevision: Long,
    val companyTimezone: String,
    val today: LocalDate,
    val scheduledFor: Instant,
    val validUntil: Instant,
    val basisRevision: String,
    val attempts: Int,
    val blockers: List<String>,
)
