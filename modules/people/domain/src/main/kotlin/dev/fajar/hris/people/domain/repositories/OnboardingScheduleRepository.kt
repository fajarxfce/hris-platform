package dev.fajar.hris.people.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import java.time.Instant
import java.util.UUID

interface OnboardingScheduleRepository {
    fun forEmployee(companyId: UUID, employmentId: UUID): Result<List<OnboardingScheduleDetails>>

    fun find(companyId: UUID, id: UUID): Result<OnboardingScheduleDetails?>

    fun forJob(companyId: UUID, jobId: UUID): Result<OnboardingSchedule?>

    fun create(actor: Actor, schedule: OnboardingSchedule): Result<MutationReceipt>

    fun finish(
        companyId: UUID,
        id: UUID,
        expectedVersion: Long,
        status: OnboardingScheduleStatus,
        finishedAt: Instant,
        failureCode: String? = null,
    ): Result<MutationReceipt>
}
