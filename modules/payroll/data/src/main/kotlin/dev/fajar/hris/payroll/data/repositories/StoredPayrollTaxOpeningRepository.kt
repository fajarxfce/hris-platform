package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.data.datasources.PayrollTaxOpeningDataSource
import dev.fajar.hris.payroll.data.mappers.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.PayrollTaxOpeningRepository
import dev.fajar.hris.schema.tables.records.*
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

class StoredPayrollTaxOpeningRepository(
    private val source: PayrollTaxOpeningDataSource,
    private val json: ObjectMapper,
) : PayrollTaxOpeningRepository {
    override fun revision(
        company: UUID,
        employee: UUID,
        year: Int,
        revision: Long,
    ): Result<PayrollTaxOpening?> = safeDatabaseCall {
        source.revision(company, employee, year, revision)?.toOpening(json)
    }

    override fun find(company: UUID, employee: UUID, year: Int): Result<PayrollTaxOpening?> =
        safeDatabaseCall {
            source.find(company, employee, year)?.toOpening(json)
        }

    override fun history(
        company: UUID,
        employee: UUID,
        year: Int,
        after: Long?,
        limit: Int,
    ): Result<Page<PayrollTaxOpening>> = safeDatabaseCall {
        val rows = source.history(company, employee, year, after, limit + 1)
        Page(
            rows.take(limit).map { it.toOpening(json) },
            if (rows.size > limit) rows[limit - 1].revision.revision.toString() else null,
        )
    }

    override fun list(
        company: UUID,
        year: Int,
        status: PayrollTaxOpeningStatus?,
        after: UUID?,
        limit: Int,
    ): Result<Page<PayrollTaxOpening>> = safeDatabaseCall {
        val rows = source.list(company, year, status?.name, after, limit + 1)
        Page(
            rows.take(limit).map { it.toOpening(json) },
            if (rows.size > limit) rows[limit - 1].employeeId.toString() else null,
        )
    }

    override fun authors(company: UUID, id: UUID): Result<Set<UUID>> = safeDatabaseCall {
        java.util.Set.copyOf(source.authors(company, id))
    }

    override fun save(
        company: UUID,
        opening: PayrollTaxOpening,
        expectedVersion: Long?,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                if (expectedVersion == null) {
                    source.insert(
                        PayrollTaxOpeningsRecord().also {
                            it.companyId = company
                            it.id = opening.id
                            it.employmentId = opening.employeeId
                            it.taxYear = opening.year
                            it.version = 0
                        }
                    )
                    0L
                } else source.advance(company, opening.id, expectedVersion)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.append(
                        PayrollTaxOpeningRevisionsRecord().also {
                            it.companyId = company
                            it.openingId = opening.id
                            it.revision = version
                            it.status = opening.status.name
                            it.terms =
                                JSONB.valueOf(json.writeValueAsString(opening.terms.toData()))
                            it.preparedBy = opening.preparedBy
                            it.verifiedBy = opening.verifiedBy
                            it.actorId = opening.verifiedBy ?: opening.preparedBy
                            it.reason = opening.reason
                            it.recordedAt = opening.recordedAt.atOffset(ZoneOffset.UTC)
                        }
                    )
                    MutationReceipt(opening.id, version)
                }
            }
}
