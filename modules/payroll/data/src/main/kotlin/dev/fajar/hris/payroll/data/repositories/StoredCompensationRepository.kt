package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.data.datasources.CompensationDataSource
import dev.fajar.hris.payroll.data.mappers.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.CompensationRepository
import dev.fajar.hris.schema.tables.records.*
import java.time.YearMonth
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

class StoredCompensationRepository(
    private val source: CompensationDataSource,
    private val json: ObjectMapper,
) : CompensationRepository {
    override fun revision(
        companyId: UUID,
        employeeId: UUID,
        revision: Long,
    ): Result<EmployeeCompensation?> = safeDatabaseCall {
        source.revision(companyId, employeeId, revision)?.toCompensation(json)
    }

    override fun current(companyId: UUID, employeeId: UUID): Result<EmployeeCompensation?> =
        safeDatabaseCall {
            source.current(companyId, employeeId)?.toCompensation(json)
        }

    override fun effective(
        companyId: UUID,
        employeeId: UUID,
        month: YearMonth,
    ): Result<EmployeeCompensation?> = safeDatabaseCall {
        source.effective(companyId, employeeId, month.atDay(1))?.toCompensation(json)
    }

    override fun list(
        companyId: UUID,
        month: YearMonth,
        after: UUID?,
        limit: Int,
    ): Result<Page<EmployeeCompensation>> = safeDatabaseCall {
        val rows = source.list(companyId, month.atDay(1), after, limit + 1)
        Page(
            rows.take(limit).map { it.toCompensation(json) },
            if (rows.size > limit) rows[limit - 1].revision.employmentId.toString() else null,
        )
    }

    override fun history(
        companyId: UUID,
        employeeId: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<CompensationRevision>> = safeDatabaseCall {
        val rows = source.history(companyId, employeeId, after, limit + 1)
        Page(
            rows.take(limit).map { it.toHistory(json) },
            if (rows.size > limit) rows[limit - 1].revision.revision.toString() else null,
        )
    }

    override fun save(
        actor: Actor,
        compensation: EmployeeCompensation,
        expectedVersion: Long?,
        reason: String,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                val company = requireNotNull(actor.companyId)
                if (expectedVersion == null) {
                    source.insert(
                        EmployeeCompensationsRecord().also {
                            it.companyId = company
                            it.employmentId = compensation.employeeId
                            it.version = 0
                        }
                    )
                    0L
                } else source.advance(company, compensation.employeeId, expectedVersion)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.append(
                        EmployeeCompensationRevisionsRecord().also {
                            it.companyId = requireNotNull(actor.companyId)
                            it.employmentId = compensation.employeeId
                            it.revision = version
                            it.effectiveFrom = compensation.effectiveFrom.atDay(1)
                            it.employeeNumber = compensation.employeeNumber
                            it.employeeName = compensation.employeeName
                            it.terms =
                                JSONB.valueOf(json.writeValueAsString(compensation.terms.toData()))
                            it.actorId = actor.accountId
                            it.reason = reason
                        }
                    )
                    MutationReceipt(compensation.employeeId, version)
                }
            }
}
