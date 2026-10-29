package dev.fajar.hris.payroll.domain.repositories

import dev.fajar.hris.core.domain.Result
import dev.fajar.hris.payroll.domain.entities.PayrollComparisonData
import java.util.UUID

interface PayrollComparisonRepository {
    fun compare(
        company: UUID,
        baseline: UUID,
        current: UUID,
        after: UUID?,
        limit: Int,
        changedOnly: Boolean,
    ): Result<PayrollComparisonData>
}
