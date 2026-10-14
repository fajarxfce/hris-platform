package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.payroll.data.datasources.PayrollAmendmentDataSource
import dev.fajar.hris.payroll.data.mappers.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.payroll.domain.repositories.PayrollAmendmentRepository
import java.time.LocalDate
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredPayrollAmendmentRepository(
    private val source: PayrollAmendmentDataSource,
    private val json: ObjectMapper,
) : PayrollAmendmentRepository {
    override fun summary(company: UUID, id: UUID): Result<PayrollAmendmentSummary?> =
        safeDatabaseCall {
            source.summary(company, id)?.let {
                require(it.header.companyId == company && it.header.id == id)
                it.toAmendmentSummary()
            }
        }

    override fun find(company: UUID, id: UUID): Result<PayrollAmendment?> = safeDatabaseCall {
        source.find(company, id)?.let {
            require(it.summary.header.companyId == company && it.summary.header.id == id)
            it.toAmendment(json)
        }
    }

    override fun revision(company: UUID, id: UUID, version: Long): Result<PayrollAmendment?> =
        safeDatabaseCall {
            source.revision(company, id, version)?.let {
                require(
                    it.summary.header.companyId == company &&
                        it.summary.header.id == id &&
                        it.summary.revision == version
                )
                it.toAmendment(json)
            }
        }

    override fun history(
        company: UUID,
        id: UUID,
        after: Long?,
        limit: Int,
    ): Result<Page<PayrollAmendmentSummary>> = safeDatabaseCall {
        val rows = source.history(company, id, after, limit + 1)
        require(
            rows.size <= limit + 1 &&
                rows.all { it.header.companyId == company && it.header.id == id }
        )
        Page(
            java.util.List.copyOf(rows.take(limit).map { it.toAmendmentSummary() }),
            if (rows.size > limit) rows[limit - 1].revision.toString() else null,
        )
    }

    override fun list(
        company: UUID,
        year: Int,
        employeeId: UUID?,
        status: PayrollAmendmentStatus?,
        query: String,
        excludedAccountId: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<PayrollAmendmentSummary>> = safeDatabaseCall {
        val rows =
            source.list(
                company,
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31),
                employeeId,
                status?.name,
                query,
                excludedAccountId,
                after,
                limit + 1,
            )
        require(
            rows.size <= limit + 1 &&
                rows.all { it.header.companyId == company && it.header.firstMonth.year == year }
        )
        Page(
            java.util.List.copyOf(rows.take(limit).map { it.toAmendmentSummary() }),
            if (rows.size > limit) rows[limit - 1].header.id.toString() else null,
        )
    }

    override fun save(
        company: UUID,
        amendment: PayrollAmendment,
        expectedVersion: Long?,
    ): Result<MutationReceipt> =
        safeDatabaseCall {
                require(amendment.summary.version == (expectedVersion ?: -1) + 1)
                if (expectedVersion == null) {
                    source.insert(amendment.summary.toHeaderRecord(company))
                    0L
                } else source.advance(company, amendment.summary.id, expectedVersion)
            }
            .requireCurrentVersion()
            .flatMap { version ->
                safeDatabaseCall {
                    source.append(amendment.toRevisionRecord(company, json))
                    MutationReceipt(amendment.summary.id, version)
                }
            }

    override fun calculationSummary(
        company: UUID,
        amendmentId: UUID,
        calculationId: UUID,
    ): Result<PayrollAmendmentCalculationSummary?> = safeDatabaseCall {
        source.calculationSummary(company, amendmentId, calculationId)?.let {
            require(
                it.companyId == company && it.amendmentId == amendmentId && it.id == calculationId
            )
            it.toAmendmentCalculationSummary(json)
        }
    }

    override fun calculation(
        company: UUID,
        amendmentId: UUID,
        calculationId: UUID,
    ): Result<PayrollAmendmentCalculationAttempt?> = safeDatabaseCall {
        source.calculation(company, amendmentId, calculationId)?.let {
            require(
                it.summary.companyId == company &&
                    it.summary.amendmentId == amendmentId &&
                    it.summary.id == calculationId
            )
            it.toAmendmentAttempt(source.results(company, calculationId), json)
        }
    }

    override fun recordCalculation(
        company: UUID,
        attempt: PayrollAmendmentCalculationAttempt,
    ): Result<Unit> = safeDatabaseCall {
        require(
            (attempt.summary.status == PayrollAmendmentCalculationStatus.SUCCEEDED) ==
                (attempt.preview != null)
        )
        require(
            attempt.summary.assessmentCount == (attempt.preview?.calculations?.size ?: 0) &&
                attempt.summary.assessmentCount in 0..12
        )
        source.insertCalculation(attempt.summary.toRecord(company, json))
        attempt.preview?.let { preview ->
            source.insertResults(
                preview.calculations.mapIndexed { index, result ->
                    result.toRecord(company, attempt.summary.id, index + 1, json)
                }
            )
        }
        Unit
    }
}
