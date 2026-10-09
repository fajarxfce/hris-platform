package dev.fajar.hris.workforce.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.workforce.data.datasources.OvertimeDataSource
import dev.fajar.hris.workforce.data.mappers.*
import dev.fajar.hris.workforce.domain.entities.*
import dev.fajar.hris.workforce.domain.repositories.OvertimeRepository
import java.time.*
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredOvertimeRepository(
    private val source: OvertimeDataSource,
    private val json: ObjectMapper,
) : OvertimeRepository {
    override fun lock(companyId: UUID, employeeId: UUID, shared: Boolean): Result<Unit> =
        safeDatabaseCall {
            source.lock(companyId, employeeId, shared)
        }

    override fun find(companyId: UUID, id: UUID): Result<OvertimeRequest?> = safeDatabaseCall {
        source.find(companyId, id)?.toOvertime(json)
    }

    override fun count(companyId: UUID, employeeId: UUID, month: YearMonth): Result<Int> =
        safeDatabaseCall {
            source.count(companyId, employeeId, month.atDay(1), month.atEndOfMonth())
        }

    override fun overlaps(
        companyId: UUID,
        employeeId: UUID,
        interval: OvertimeInterval,
    ): Result<Boolean> = safeDatabaseCall {
        source.overlaps(
            companyId,
            employeeId,
            interval.startsAt.atOffset(ZoneOffset.UTC),
            interval.endsAt.atOffset(ZoneOffset.UTC),
        )
    }

    override fun unresolved(companyId: UUID, month: YearMonth): Result<Boolean> = safeDatabaseCall {
        source.unresolved(companyId, month.atDay(1), month.atEndOfMonth())
    }

    override fun approved(
        companyId: UUID,
        employeeId: UUID,
        month: YearMonth,
    ): Result<List<OvertimeRequest>> = safeDatabaseCall {
        source.approved(companyId, employeeId, month.atDay(1), month.atEndOfMonth()).map {
            it.toOvertime(json)
        }
    }

    override fun list(
        companyId: UUID,
        employeeId: UUID?,
        from: LocalDate,
        until: LocalDate,
        status: OvertimeStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<OvertimeRequest>> = safeDatabaseCall {
        val rows = source.list(companyId, employeeId, from, until, status?.name, after, limit + 1)
        Page(
            rows.take(limit).map { it.toOvertime(json) },
            if (rows.size > limit) rows[limit - 1].id.toString() else null,
        )
    }

    override fun history(
        companyId: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<OvertimeChange>> = safeDatabaseCall {
        val rows = source.history(companyId, id, after, limit + 1)
        Page(
            rows.take(limit).map { it.toChange() },
            if (rows.size > limit) rows[limit - 1].revision.toString() else null,
        )
    }

    override fun create(companyId: UUID, request: OvertimeRequest): Result<MutationReceipt> =
        safeDatabaseCall {
            source.insert(request.toRow(companyId, json))
            source.append(
                request.toChange(
                    companyId,
                    0,
                    OvertimeChangeKind.PLANNED,
                    request.authorId,
                    request.createdAt,
                    request.reason,
                )
            )
            MutationReceipt(request.id, 0)
        }

    override fun update(
        actor: Actor,
        request: OvertimeRequest,
        kind: OvertimeChangeKind,
        reason: String,
        at: Instant,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                val company = requireNotNull(actor.companyId)
                source.update(request.toRow(company, json), request.version)?.let { version ->
                    source.append(
                        request.toChange(company, version, kind, actor.accountId, at, reason)
                    )
                    MutationReceipt(request.id, version)
                }
            }
            .flatMap {
                it?.let { receipt -> Result.Success(receipt) }
                    ?: Result.Failed(Failure(FailureKind.CONFLICT, "stale_version"))
            }
}
