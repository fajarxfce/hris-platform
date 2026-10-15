package dev.fajar.hris.people.domain.entities

data class OffboardingScheduleReview(
    val case: LifecycleCaseDetails,
    val employmentVersion: Long,
    val appliedEmploymentRevision: Long,
    val window: OffboardingWindow,
    val basisRevision: String,
    val attempts: Int,
    val blockers: List<String>,
)
