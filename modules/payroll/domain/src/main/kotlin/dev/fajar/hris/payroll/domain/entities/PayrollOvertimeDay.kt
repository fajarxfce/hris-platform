package dev.fajar.hris.payroll.domain.entities

import java.time.LocalDate

data class PayrollOvertimeDay(val date: LocalDate, val calculation: OvertimePay)
