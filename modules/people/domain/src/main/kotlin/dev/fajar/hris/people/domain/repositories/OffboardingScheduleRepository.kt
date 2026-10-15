package dev.fajar.hris.people.domain.repositories

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.domain.entities.*
import java.time.Instant
import java.util.UUID

interface OffboardingScheduleRepository {
    /** At most eight retained attempts, under the owning people/case guard. */
    fun forCase(companyId: UUID, caseId: UUID): Result<List<OffboardingScheduleDetails>>

    fun find(companyId: UUID, id: UUID): Result<OffboardingScheduleDetails?>

    fun forJob(companyId: UUID, jobId: UUID): Result<OffboardingSchedule?>

    fun create(actor: Actor, schedule: OffboardingSchedule): Result<MutationReceipt>

    fun finish(
        companyId: UUID,
        id: UUID,
        expectedVersion: Long,
        status: OffboardingScheduleStatus,
        finishedAt: Instant,
        failureCode: String?,
    ): Result<MutationReceipt>
}
