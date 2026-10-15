package dev.fajar.hris.people.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.people.data.datasources.OffboardingScheduleDataSource
import dev.fajar.hris.people.data.mappers.*
import dev.fajar.hris.people.domain.entities.*
import dev.fajar.hris.people.domain.repositories.OffboardingScheduleRepository
import java.time.*
import java.util.UUID

class StoredOffboardingScheduleRepository(private val source: OffboardingScheduleDataSource) :
    OffboardingScheduleRepository {
    override fun forCase(companyId: UUID, caseId: UUID) = safeDatabaseCall {
        val rows = source.forCase(companyId, caseId, 9)
        check(rows.size <= 8)
        rows.map { it.toOffboardingScheduleDetails() }
    }

    override fun find(companyId: UUID, id: UUID) = safeDatabaseCall {
        source.find(companyId, id)?.toOffboardingScheduleDetails()
    }

    override fun forJob(companyId: UUID, jobId: UUID) = safeDatabaseCall {
        source.forJob(companyId, jobId)?.toOffboardingSchedule()
    }

    override fun create(actor: Actor, schedule: OffboardingSchedule) = safeDatabaseCall {
        check(schedule.createdBy == actor.accountId)
        source.insert(schedule.toRow(requireNotNull(actor.companyId)))
        MutationReceipt(schedule.id, schedule.version)
    }

    override fun finish(
        companyId: UUID,
        id: UUID,
        expectedVersion: Long,
        status: OffboardingScheduleStatus,
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
