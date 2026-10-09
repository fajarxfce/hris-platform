package dev.fajar.hris.payroll.delivery.responses

import java.time.*

data class PayrollOvertimeDayResponse(val date: LocalDate, val calculation: OvertimePayResponse)
