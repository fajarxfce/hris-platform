package dev.fajar.hris.people.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.data.datasources.OnboardingScheduleDataSource
import dev.fajar.hris.people.data.mappers.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.OnboardingScheduleRepository
import java.time.*
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredOnboardingScheduleRepository(
    private val source: OnboardingScheduleDataSource,
    private val json: ObjectMapper,
) : OnboardingScheduleRepository {
    override fun forEmployee(companyId: UUID, employmentId: UUID) = safeDatabaseCall {
        val rows = source.forEmployee(companyId, employmentId, 9)
        check(rows.size <= 8)
        rows.map { it.toOnboardingScheduleDetails(json) }
    }

    override fun find(companyId: UUID, id: UUID) = safeDatabaseCall {
        source.find(companyId, id)?.toOnboardingScheduleDetails(json)
    }

    override fun forJob(companyId: UUID, jobId: UUID) = safeDatabaseCall {
        source.forJob(companyId, jobId)?.toOnboardingSchedule(json)
    }

    override fun create(actor: Actor, schedule: OnboardingSchedule) = safeDatabaseCall {
        check(schedule.createdBy == actor.accountId)
        source.insert(schedule.toRow(requireNotNull(actor.companyId), json))
        MutationReceipt(schedule.id, schedule.version)
    }

    override fun finish(
        companyId: UUID,
        id: UUID,
        expectedVersion: Long,
        status: OnboardingScheduleStatus,
        finishedAt: Instant,
        failureCode: String?,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source.finish(
                    companyId,
                    id,
                    expectedVersion,
                    status.name,
                    OffsetDateTime.ofInstant(finishedAt, ZoneOffset.UTC),
                    failureCode,
                )
            }
            .flatMap { version ->
                if (version == null) Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
                else Result.Success(MutationReceipt(id, version))
            }
}
