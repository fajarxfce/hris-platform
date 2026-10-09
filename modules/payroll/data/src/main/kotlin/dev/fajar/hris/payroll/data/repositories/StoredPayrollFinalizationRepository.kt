package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.data.datasources.PayrollFinalizationDataSource
import dev.fajar.hris.payroll.data.mappers.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.PayrollFinalizationRepository
import java.time.*
import java.util.UUID

class StoredPayrollFinalizationRepository(private val source: PayrollFinalizationDataSource) :
    PayrollFinalizationRepository {
    override fun find(company: UUID, id: UUID): Result<PayrollFinalization?> = safeDatabaseCall {
        source.find(company, id)?.toFinalization()
    }

    override fun forJob(company: UUID, job: UUID): Result<PayrollFinalization?> = safeDatabaseCall {
        source.forJob(company, job)?.toFinalization()
    }

    override fun latest(company: UUID, run: UUID): Result<PayrollFinalization?> = safeDatabaseCall {
        source.latest(company, run)?.toFinalization()
    }

    override fun list(
        company: UUID,
        run: UUID,
        after: Int?,
        limit: Int,
    ): Result<Page<PayrollFinalization>> = safeDatabaseCall {
        val rows = source.list(company, run, after, limit + 1)
        Page(
            rows.take(limit).map { it.toFinalization() },
            if (rows.size > limit) rows[limit - 1].attempt.toString() else null,
        )
    }

    override fun readiness(company: UUID, run: UUID): Result<PayrollPublicationReadiness> =
        safeDatabaseCall {
            source.readiness(company, run).let {
                PayrollPublicationReadiness(
                    it.changedEmployments,
                    it.duplicatePeople,
                    it.staleTaxHistories,
                    it.assessedHolidays,
                )
            }
        }

    override fun create(company: UUID, finalization: PayrollFinalization): Result<Unit> =
        safeDatabaseCall {
            source.insert(finalization.toRecord(company))
        }

    override fun publish(
        company: UUID,
        finalization: PayrollFinalization,
        at: Instant,
    ): Result<Unit> =
        safeDatabaseCall {
                val timestamp = at.atOffset(ZoneOffset.UTC)
                source.insertAssessments(company, finalization.id, timestamp)
                source.publish(company, finalization.id, timestamp)
            }
            .flatMap { changed ->
                if (changed) Result.Success(Unit)
                else Result.Failed(Failure(FailureKind.CONFLICT, "payroll_finalization_obsolete"))
            }
}
