package dev.fajar.hris.payroll.data.models

import java.time.*

data class PayrollOvertimeDayData(val date: LocalDate, val calculation: OvertimePayData)
