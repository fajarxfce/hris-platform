package dev.fajar.hris.people.domain.entities

import java.time.Instant
import java.util.UUID

/** An authorization to create a case. STARTED means creation succeeded, not that tasks are done. */
data class OnboardingSchedule(
    val id: UUID,
    val caseId: UUID,
    val employmentId: UUID,
    val employmentVersion: Long,
    val appliedEmploymentRevision: Long,
    val selection: OnboardingScheduleSelection,
    val template: LifecycleTemplate,
    val assignees: Map<String, UUID>,
    val companyTimezone: String,
    val scheduledFor: Instant,
    val validUntil: Instant,
    val attempt: Int,
    val jobId: UUID,
    val createdBy: UUID,
    val createdAt: Instant,
    val reason: String,
    val status: OnboardingScheduleStatus = OnboardingScheduleStatus.SCHEDULED,
    val version: Long = 0,
    val finishedAt: Instant? = null,
    val failureCode: String? = null,
)
