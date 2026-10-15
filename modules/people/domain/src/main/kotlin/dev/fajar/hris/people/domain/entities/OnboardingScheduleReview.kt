package dev.fajar.hris.people.domain.entities

data class OnboardingScheduleReview(
    val employee: LifecycleEmployeeReference,
    val employmentVersion: Long,
    val appliedEmploymentRevision: Long,
    val selection: OnboardingScheduleSelection,
    val template: LifecycleTemplate,
    val window: OnboardingWindow,
    val basisRevision: String,
    val attempts: Int,
    val blockers: List<String>,
)
