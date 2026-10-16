package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.PayrollWorkAdjustmentRow
import dev.fajar.hris.payroll.domain.entities.PayrollWorkAdjustment
import java.time.YearMonth

fun PayrollWorkAdjustmentRow.toWorkAdjustment(month: YearMonth): PayrollWorkAdjustment {
    require(revision >= 1 && YearMonth.from(workDate) == month)
    require(reason.isNotBlank() && reason.length <= 1000)
    return PayrollWorkAdjustment(id, revision, workDate, recordedAt.toInstant(), reason)
}
