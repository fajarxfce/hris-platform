package dev.fajar.hris.payroll.domain.entities

import java.time.YearMonth
import java.util.UUID

/** Exact Workforce evidence acquired for one published assessment. */
data class PayrollAmendmentAttendance(
    val companyId: UUID,
    val assessmentId: UUID,
    val employeeId: UUID,
    val workJobId: UUID,
    val month: YearMonth,
    val adjustment: PayrollWorkAdjustment,
    val latestAdjustmentId: UUID,
    val days: List<PayrollWorkDay>,
)
