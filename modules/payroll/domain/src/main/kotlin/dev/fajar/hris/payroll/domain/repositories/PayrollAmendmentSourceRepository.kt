package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.payroll.domain.entities.PayrollAmendmentAttendance
import dev.fajar.hris.payroll.domain.entities.PayrollAmendmentCandidate
import dev.fajar.hris.payroll.domain.entities.PayrollAmendmentSource
import java.util.UUID

interface PayrollAmendmentSourceRepository {
    /** Shared month guards for the selected published year, acquired before People/access. */
    fun lockWorkPeriods(company: UUID, assessmentId: UUID): Result<Unit>

    /** Resolve only the retained assessment identity; do not acquire private draft terms. */
    fun lockAmendmentWorkPeriods(company: UUID, amendmentId: UUID): Result<Unit>

    /** Exact immutable attendance references keyed by their published assessment. */
    fun attendance(
        company: UUID,
        references: Map<UUID, UUID>,
    ): Result<List<PayrollAmendmentAttendance>>

    fun candidates(
        company: UUID,
        year: Int,
        employeeId: UUID?,
        query: String,
        excludedAccountId: UUID,
        after: UUID?,
        limit: Int,
    ): Result<Page<PayrollAmendmentCandidate>>

    /** The complete published year for the selected assessment, in chronological order. */
    fun sources(company: UUID, assessmentId: UUID): Result<List<PayrollAmendmentSource>>
}
