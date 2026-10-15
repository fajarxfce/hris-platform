package dev.fajar.hris.people.delivery.responses

import java.time.Instant
import java.time.LocalDate

data class OnboardingScheduleReviewResponse(
    val employee: LifecycleEmployeeResponse,
    val employmentVersion: Long,
    val appliedEmploymentRevision: Long,
    val template: LifecycleTemplateResponse,
    val targetDate: LocalDate,
    val startsOn: LocalDate,
    val companyTimezone: String,
    val today: LocalDate,
    val scheduledFor: Instant,
    val validUntil: Instant,
    val basisRevision: String,
    val attempts: Int,
    val blockers: List<String>,
)
