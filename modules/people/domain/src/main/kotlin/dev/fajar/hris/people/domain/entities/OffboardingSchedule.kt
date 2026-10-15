package dev.fajar.hris.people.domain.entities

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** An immutable authorization basis with one terminal outcome. Job progress has its own version. */
data class OffboardingSchedule(
    val id: UUID,
    val caseId: UUID,
    val employmentId: UUID,
    val caseVersion: Long,
    val employmentVersion: Long,
    val appliedEmploymentRevision: Long,
    val employeeAccountId: UUID?,
    val lastWorkingDate: LocalDate,
    val companyTimezone: String,
    val scheduledFor: Instant,
    val validUntil: Instant,
    val attempt: Int,
    val jobId: UUID,
    val createdBy: UUID,
    val createdAt: Instant,
    val reason: String,
    val status: OffboardingScheduleStatus = OffboardingScheduleStatus.SCHEDULED,
    val version: Long = 0,
    val finishedAt: Instant? = null,
    val failureCode: String? = null,
)
