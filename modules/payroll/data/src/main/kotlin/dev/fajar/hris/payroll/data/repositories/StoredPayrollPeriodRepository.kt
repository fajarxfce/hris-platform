package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.data.datasources.PayrollPeriodDataSource
import dev.fajar.hris.payroll.data.mappers.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.PayrollPeriodRepository
import dev.fajar.hris.schema.tables.records.*
import java.time.*
import java.util.UUID

class StoredPayrollPeriodRepository(private val source: PayrollPeriodDataSource) :
    PayrollPeriodRepository {
    override fun transition(
        company: UUID,
        period: PayrollPeriod,
        status: PayrollPeriodStatus,
        runId: UUID?,
        actor: UUID,
        at: Instant,
        reason: String,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                source.transition(company, period.id, period.version, status.name, runId)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.append(
                        PayrollPeriodChangesRecord().also {
                            it.companyId = company
                            it.periodId = period.id
                            it.revision = version
                            it.status = status.name
                            it.runId = runId
                            it.actorId = actor
                            it.recordedAt = at.atOffset(ZoneOffset.UTC)
                            it.reason = reason
                        }
                    )
                    MutationReceipt(period.id, version)
                }
            }

    override fun find(company: UUID, id: UUID): Result<PayrollPeriod?> = safeDatabaseCall {
        source.find(company, id)?.toPeriod()
    }

    override fun active(company: UUID, month: YearMonth): Result<Boolean> = safeDatabaseCall {
        source.active(company, month.atDay(1))
    }

    override fun count(company: UUID, month: YearMonth): Result<Int> = safeDatabaseCall {
        source.count(company, month.atDay(1))
    }

    override fun create(
        company: UUID,
        period: PayrollPeriod,
        employees: List<UUID>,
        reason: String,
    ): Result<MutationReceipt> = safeDatabaseCall {
        source.insert(
            PayrollPeriodsRecord().also {
                it.companyId = company
                it.id = period.id
                it.earningsMonth = period.earningsMonth.atDay(1)
                it.plannedPaymentDate = period.plannedPaymentDate
                it.timezone = period.timezone
                it.participantCount = period.participantCount
                it.authorId = period.authorId
                it.createdAt = period.createdAt.atOffset(ZoneOffset.UTC)
                it.status = period.status.name
                it.version = 0
            }
        )
        source.insertMembers(
            employees.map { id ->
                PayrollPeriodMembersRecord().also {
                    it.companyId = company
                    it.periodId = period.id
                    it.employmentId = id
                }
            }
        )
        source.append(
            PayrollPeriodChangesRecord().also {
                it.companyId = company
                it.periodId = period.id
                it.revision = 0
                it.status = "DRAFT"
                it.actorId = period.authorId
                it.recordedAt = period.createdAt.atOffset(ZoneOffset.UTC)
                it.reason = reason
            }
        )
        MutationReceipt(period.id, 0)
    }

    override fun cancel(
        company: UUID,
        period: PayrollPeriod,
        actor: UUID,
        at: Instant,
        reason: String,
    ): Result<MutationReceipt> =
        safeDatabaseCall { source.cancel(company, period.id, period.version) }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.append(
                        PayrollPeriodChangesRecord().also {
                            it.companyId = company
                            it.periodId = period.id
                            it.revision = version
                            it.status = "CANCELLED"
                            it.actorId = actor
                            it.recordedAt = at.atOffset(ZoneOffset.UTC)
                            it.reason = reason
                        }
                    )
                    MutationReceipt(period.id, version)
                }
            }

    override fun members(
        company: UUID,
        id: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<PayrollPeriodMember>> = safeDatabaseCall {
        val rows = source.members(company, id, after, limit + 1)
        Page(
            rows.take(limit).map { it.toMember() },
            if (rows.size > limit) rows[limit - 1].employeeId.toString() else null,
        )
    }

    override fun history(
        company: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<PayrollPeriodChange>> = safeDatabaseCall {
        val rows = source.history(company, id, after, limit + 1)
        Page(
            rows.take(limit).map { it.toChange() },
            if (rows.size > limit) rows[limit - 1].revision.toString() else null,
        )
    }

    override fun list(
        company: UUID,
        from: YearMonth,
        until: YearMonth,
        status: PayrollPeriodStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<PayrollPeriod>> = safeDatabaseCall {
        val rows =
            source.list(company, from.atDay(1), until.atDay(1), status?.name, after, limit + 1)
        Page(
            rows.take(limit).map { it.toPeriod() },
            if (rows.size > limit) rows[limit - 1].id.toString() else null,
        )
    }
}
