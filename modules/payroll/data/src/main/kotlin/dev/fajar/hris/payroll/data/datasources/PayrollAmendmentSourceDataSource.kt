package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollAmendmentAttendanceRow
import dev.fajar.hris.payroll.data.models.PayrollAmendmentCandidateRow
import dev.fajar.hris.payroll.data.models.PayrollAmendmentSourceRow
import java.time.LocalDate
import java.util.UUID

interface PayrollAmendmentSourceDataSource {
    fun lockWorkPeriods(company: UUID, assessmentId: UUID)

    fun lockAmendmentWorkPeriods(company: UUID, amendmentId: UUID)

    fun attendance(company: UUID, references: Map<UUID, UUID>): List<PayrollAmendmentAttendanceRow>

    fun candidates(
        company: UUID,
        from: LocalDate,
        until: LocalDate,
        employeeId: UUID?,
        query: String,
        excludedAccountId: UUID,
        after: UUID?,
        limit: Int,
    ): List<PayrollAmendmentCandidateRow>

    fun sources(company: UUID, assessmentId: UUID): List<PayrollAmendmentSourceRow>
}
