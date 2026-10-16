package dev.fajar.hris.payroll.data.repositories

import dev.fajar.hris.core.database.safeDatabaseCall
import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.payroll.data.datasources.PayrollAmendmentSourceDataSource
import dev.fajar.hris.payroll.data.mappers.toAmendmentAttendance
import dev.fajar.hris.payroll.data.mappers.toAmendmentCandidate
import dev.fajar.hris.payroll.data.mappers.toAmendmentSource
import dev.fajar.hris.payroll.domain.entities.PayrollAmendmentAttendance
import dev.fajar.hris.payroll.domain.entities.PayrollAmendmentCandidate
import dev.fajar.hris.payroll.domain.entities.PayrollAmendmentSource
import dev.fajar.hris.payroll.domain.repositories.PayrollAmendmentSourceRepository
import java.time.LocalDate
import java.util.UUID
import tools.jackson.databind.ObjectMapper

class StoredPayrollAmendmentSourceRepository(
    private val source: PayrollAmendmentSourceDataSource,
    private val json: ObjectMapper,
) : PayrollAmendmentSourceRepository {
    override fun lockWorkPeriods(company: UUID, assessmentId: UUID): Result<Unit> =
        safeDatabaseCall {
            source.lockWorkPeriods(company, assessmentId)
        }

    override fun lockAmendmentWorkPeriods(company: UUID, amendmentId: UUID): Result<Unit> =
        safeDatabaseCall {
            source.lockAmendmentWorkPeriods(company, amendmentId)
        }

    override fun attendance(
        company: UUID,
        references: Map<UUID, UUID>,
    ): Result<List<PayrollAmendmentAttendance>> = safeDatabaseCall {
        require(references.size <= 12)
        val rows = if (references.isEmpty()) emptyList() else source.attendance(company, references)
        require(
            rows.size <= references.size &&
                rows.map { it.assessmentId }.distinct().size == rows.size
        )
        require(
            rows.all { it.companyId == company && references[it.assessmentId] == it.adjustment.id }
        )
        java.util.List.copyOf(rows.map { it.toAmendmentAttendance(json) })
    }

    override fun candidates(
        company: UUID,
        year: Int,
        employeeId: UUID?,
        query: String,
        excludedAccountId: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<PayrollAmendmentCandidate>> = safeDatabaseCall {
        val rows =
            source.candidates(
                company,
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31),
                employeeId,
                query,
                excludedAccountId,
                after,
                limit + 1,
            )
        require(rows.size <= limit + 1)
        require(
            rows.all {
                it.companyId == company &&
                    it.taxMonth.year == year &&
                    (employeeId == null || it.employeeId == employeeId) &&
                    (after == null || it.assessmentId.toString() > after.toString())
            }
        )
        require(
            rows.zipWithNext().all { (before, next) ->
                before.assessmentId.toString() < next.assessmentId.toString()
            }
        )
        val entries = rows.map { it.toAmendmentCandidate() }
        Page(
            java.util.List.copyOf(entries.take(limit)),
            if (rows.size > limit) rows[limit - 1].assessmentId.toString() else null,
        )
    }

    override fun sources(company: UUID, assessmentId: UUID): Result<List<PayrollAmendmentSource>> =
        safeDatabaseCall {
            val rows = source.sources(company, assessmentId)
            require(rows.size <= 12 && rows.all { it.companyId == company })
            require(rows.isEmpty() || rows.any { it.assessmentId == assessmentId })
            require(rows.map { it.personId }.distinct().size <= 1)
            require(rows.map { it.taxMonth.year }.distinct().size <= 1)
            require(rows.map { it.assessmentId }.distinct().size == rows.size)
            require(rows.zipWithNext().all { (before, after) -> before.taxMonth < after.taxMonth })
            java.util.List.copyOf(rows.map { it.toAmendmentSource(json) })
        }
}
