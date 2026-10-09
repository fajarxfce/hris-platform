package dev.fajar.hris.payroll.domain.entities

import java.time.YearMonth
import java.util.UUID

data class PayrollPayslipCursor(val month: YearMonth, val id: UUID)
