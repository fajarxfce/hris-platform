package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.data.datasources.PayrollAmendmentPublicationDataSource
import dev.fajar.hris.payroll.data.mappers.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.PayrollAmendmentPublicationRepository
import java.time.*
import java.util.UUID

class StoredPayrollAmendmentPublicationRepository(
    private val source: PayrollAmendmentPublicationDataSource
) : PayrollAmendmentPublicationRepository {
    override fun find(
        company: UUID,
        amendmentId: UUID,
        id: UUID,
    ): Result<PayrollAmendmentPublication?> = safeDatabaseCall {
        source.find(company, amendmentId, id)?.let {
            require(it.companyId == company && it.amendmentId == amendmentId && it.id == id)
            it.toAmendmentPublication()
        }
    }

    override fun forJob(company: UUID, jobId: UUID): Result<PayrollAmendmentPublication?> =
        safeDatabaseCall {
            source.forJob(company, jobId)?.let {
                require(it.companyId == company && it.jobId == jobId)
                it.toAmendmentPublication()
            }
        }

    override fun list(company: UUID, amendmentId: UUID): Result<List<PayrollAmendmentPublication>> =
        safeDatabaseCall {
            val rows = source.list(company, amendmentId)
            require(
                rows.size <= 8 &&
                    rows.all { it.companyId == company && it.amendmentId == amendmentId }
            )
            java.util.List.copyOf(rows.map { it.toAmendmentPublication() })
        }

    override fun create(company: UUID, publication: PayrollAmendmentPublication): Result<Unit> =
        safeDatabaseCall {
            source.insert(publication.toRecord(company))
        }

    override fun publish(
        company: UUID,
        publication: PayrollAmendmentPublication,
        at: Instant,
    ): Result<Unit> =
        safeDatabaseCall {
                source.finish(
                    company,
                    publication.id,
                    "PUBLISHED",
                    at.atOffset(ZoneOffset.UTC),
                    publication.actorId,
                    null,
                )
            }
            .flatMap { changed ->
                if (!changed)
                    Result.Failed(
                        Failure(FailureKind.CONFLICT, "payroll_amendment_publication_obsolete")
                    )
                else
                    safeDatabaseCall {
                        source.insertRevisions(company, publication.id, at.atOffset(ZoneOffset.UTC))
                        source.advanceHeads(company, publication.id)
                    }
            }

    override fun stop(
        company: UUID,
        publication: PayrollAmendmentPublication,
        at: Instant,
        code: String,
        actorId: UUID,
    ): Result<Unit> =
        safeDatabaseCall {
                source.finish(
                    company,
                    publication.id,
                    "STOPPED",
                    at.atOffset(ZoneOffset.UTC),
                    actorId,
                    code,
                )
            }
            .flatMap { changed ->
                if (changed) Result.Success(Unit)
                else
                    Result.Failed(
                        Failure(FailureKind.CONFLICT, "payroll_amendment_publication_obsolete")
                    )
            }
}
