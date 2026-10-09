package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.data.datasources.PayrollInputDataSource
import dev.fajar.hris.payroll.data.mappers.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.PayrollInputRepository
import dev.fajar.hris.schema.tables.records.*
import java.time.*
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

class StoredPayrollInputRepository(
    private val source: PayrollInputDataSource,
    private val json: ObjectMapper,
) : PayrollInputRepository {
    override fun find(company: UUID, employee: UUID, month: YearMonth): Result<PayrollInput?> =
        safeDatabaseCall {
            source.find(company, employee, month.atDay(1))?.toInput(json)
        }

    override fun revision(
        company: UUID,
        employee: UUID,
        month: YearMonth,
        revision: Long,
    ): Result<PayrollInput?> = safeDatabaseCall {
        source.revision(company, employee, month.atDay(1), revision)?.toInput(json)
    }

    override fun history(
        company: UUID,
        employee: UUID,
        month: YearMonth,
        after: Long?,
        limit: Int,
    ): Result<Page<PayrollInputSummary>> = safeDatabaseCall {
        val rows = source.history(company, employee, month.atDay(1), after, limit + 1)
        Page(
            rows.take(limit).map { it.toSummary() },
            if (rows.size > limit) rows[limit - 1].version.toString() else null,
        )
    }

    override fun list(
        company: UUID,
        month: YearMonth,
        status: PayrollInputStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<PayrollInputSummary>> = safeDatabaseCall {
        val rows = source.list(company, month.atDay(1), status?.name, after, limit + 1)
        Page(
            rows.take(limit).map { it.toSummary() },
            if (rows.size > limit) rows[limit - 1].employeeId.toString() else null,
        )
    }

    override fun authors(company: UUID, id: UUID): Result<Set<UUID>> = safeDatabaseCall {
        java.util.Set.copyOf(source.authors(company, id))
    }

    override fun save(
        company: UUID,
        input: PayrollInput,
        expectedVersion: Long?,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                if (expectedVersion == null) {
                    source.insert(
                        PayrollInputsRecord().also {
                            it.companyId = company
                            it.id = input.id
                            it.employmentId = input.employeeId
                            it.earningsMonth = input.earningsMonth.atDay(1)
                            it.version = 0
                        }
                    )
                    0L
                } else source.advance(company, input.id, expectedVersion)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.append(
                        PayrollInputRevisionsRecord().also {
                            it.companyId = company
                            it.inputId = input.id
                            it.revision = version
                            it.employmentId = input.employeeId
                            it.earningsMonth = input.earningsMonth.atDay(1)
                            it.workJobId = input.workJobId
                            it.workPeriodVersion = input.workPeriodVersion
                            it.employmentVersion = input.employmentVersion
                            it.status = input.status.name
                            it.terms = JSONB.valueOf(json.writeValueAsString(input.terms.toData()))
                            it.preparedBy = input.preparedBy
                            it.verifiedBy = input.verifiedBy
                            it.actorId = input.verifiedBy ?: input.preparedBy
                            it.recordedAt = input.recordedAt.atOffset(ZoneOffset.UTC)
                            it.reason = input.reason
                        }
                    )
                    MutationReceipt(input.id, version)
                }
            }
}
