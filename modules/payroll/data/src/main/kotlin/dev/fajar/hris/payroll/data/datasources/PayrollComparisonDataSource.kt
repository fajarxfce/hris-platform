package dev.fajar.hris.payroll.data.datasources

import dev.fajar.hris.payroll.data.models.PayrollComparisonRows
import java.util.UUID

interface PayrollComparisonDataSource {
    fun compare(
        company: UUID,
        baseline: UUID,
        current: UUID,
        after: UUID?,
        limit: Int,
        changedOnly: Boolean,
    ): PayrollComparisonRows
}
