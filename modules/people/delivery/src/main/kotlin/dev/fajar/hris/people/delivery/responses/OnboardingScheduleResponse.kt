package dev.fajar.hris.people.delivery.responses

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class OnboardingScheduleResponse(
    val id: UUID,
    val caseId: UUID,
    val employmentId: UUID,
    val status: String,
    val version: Long,
    val employmentVersion: Long,
    val appliedEmploymentRevision: Long,
    val template: LifecycleTemplateResponse,
    val assignees: Map<String, UUID>,
    val targetDate: LocalDate,
    val startsOn: LocalDate,
    val companyTimezone: String,
    val scheduledFor: Instant,
    val validUntil: Instant,
    val attempt: Int,
    val createdBy: UUID,
    val createdAt: Instant,
    val reason: String,
    val finishedAt: Instant?,
    val failureCode: String?,
    val job: OnboardingScheduleJobResponse,
    val actions: Set<String>,
)
