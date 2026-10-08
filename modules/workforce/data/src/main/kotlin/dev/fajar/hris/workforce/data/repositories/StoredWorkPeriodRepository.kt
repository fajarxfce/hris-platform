package dev.fajar.hris.workforce.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.schema.tables.records.*
import dev.fajar.hris.workforce.data.datasources.WorkPeriodDataSource
import dev.fajar.hris.workforce.data.mappers.*
import dev.fajar.hris.workforce.data.models.WorkPeriodSnapshotData
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.repositories.WorkPeriodRepository
import java.time.*
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

class StoredWorkPeriodRepository(
    private val source: WorkPeriodDataSource,
    private val json: ObjectMapper,
) : WorkPeriodRepository {
    override fun lockMonth(
        companyId: UUID,
        month: YearMonth,
        exclusive: Boolean,
    ): Result<WorkPeriod> = safeDatabaseCall {
        source.ensure(companyId, month.atDay(1))
        requireNotNull(source.find(companyId, month.atDay(1), if (exclusive) "UPDATE" else "SHARE"))
            .toPeriod()
    }

    override fun lockRange(companyId: UUID, from: YearMonth): Result<List<WorkPeriod>> =
        safeDatabaseCall {
            source.range(companyId, from.atDay(1), null, true).map { it.toPeriod() }
        }

    override fun find(companyId: UUID, month: YearMonth): Result<WorkPeriod?> = safeDatabaseCall {
        source.find(companyId, month.atDay(1), null)?.toPeriod()
    }

    override fun forJob(companyId: UUID, jobId: UUID, exclusive: Boolean): Result<WorkPeriod?> =
        safeDatabaseCall {
            source.forJob(companyId, jobId, exclusive)?.toPeriod()
        }

    override fun list(
        companyId: UUID,
        from: YearMonth,
        until: YearMonth,
    ): Result<List<WorkPeriod>> = safeDatabaseCall {
        source.range(companyId, from.atDay(1), until.atDay(1), false).map { it.toPeriod() }
    }

    override fun start(
        companyId: UUID,
        period: WorkPeriod,
        jobId: UUID,
        timezone: String,
        at: Instant,
        employeeIds: List<UUID>,
    ): Result<WorkPeriod> =
        safeDatabaseCall {
                val updated =
                    source.update(
                        WorkPeriodsRecord().also {
                            it.companyId = companyId
                            it.id = period.id
                            it.status = "PROCESSING"
                            it.timezone = timezone
                            it.jobId = jobId
                            it.startedAt = at.atOffset(ZoneOffset.UTC)
                        },
                        period.version,
                    )
                if (updated != null)
                    source.insertTargets(
                        employeeIds.map { employee ->
                            WorkPeriodTargetsRecord().also {
                                it.companyId = companyId
                                it.jobId = jobId
                                it.employmentId = employee
                            }
                        }
                    )
                updated?.toPeriod()
            }
            .flatMap {
                it?.let { Result.Success(it) }
                    ?: Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            }

    override fun nextTarget(companyId: UUID, jobId: UUID, after: UUID?): Result<UUID?> =
        safeDatabaseCall {
            source.nextTarget(companyId, jobId, after)
        }

    override fun snapshot(
        companyId: UUID,
        jobId: UUID,
        employeeId: UUID,
    ): Result<WorkPeriodSnapshot?> = safeDatabaseCall {
        source.snapshot(companyId, jobId, employeeId)?.let {
            json
                .readValue(requireNotNull(it.payload).data(), WorkPeriodSnapshotData::class.java)
                .toSnapshot()
        }
    }

    override fun saveSnapshot(
        companyId: UUID,
        jobId: UUID,
        snapshot: WorkPeriodSnapshot,
    ): Result<Unit> = safeDatabaseCall {
        source.insertSnapshot(
            WorkPeriodSnapshotsRecord().also {
                it.companyId = companyId
                it.jobId = jobId
                it.employmentId = snapshot.employeeId
                it.payload = JSONB.valueOf(json.writeValueAsString(snapshot.toData()))
            }
        )
    }

    override fun snapshotCount(companyId: UUID, jobId: UUID): Result<Int> = safeDatabaseCall {
        source.countSnapshots(companyId, jobId)
    }

    override fun finish(companyId: UUID, period: WorkPeriod, at: Instant): Result<WorkPeriod> =
        safeDatabaseCall {
                source
                    .update(
                        WorkPeriodsRecord().also {
                            it.companyId = companyId
                            it.id = period.id
                            it.status = "CLOSED"
                            it.timezone = period.timezone
                            it.jobId = period.jobId
                            it.startedAt = period.startedAt?.atOffset(ZoneOffset.UTC)
                            it.closedAt = at.atOffset(ZoneOffset.UTC)
                        },
                        period.version,
                    )
                    ?.toPeriod()
            }
            .flatMap {
                it?.let { Result.Success(it) }
                    ?: Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            }

    override fun requireReview(
        companyId: UUID,
        period: WorkPeriod,
        failureCode: String,
    ): Result<WorkPeriod> =
        safeDatabaseCall {
                source
                    .update(
                        WorkPeriodsRecord().also {
                            it.companyId = companyId
                            it.id = period.id
                            it.status = "REVIEW_REQUIRED"
                            it.timezone = period.timezone
                            it.jobId = period.jobId
                            it.startedAt = period.startedAt?.atOffset(ZoneOffset.UTC)
                            it.failureCode = failureCode
                        },
                        period.version,
                    )
                    ?.toPeriod()
            }
            .flatMap {
                it?.let { Result.Success(it) }
                    ?: Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            }
}
