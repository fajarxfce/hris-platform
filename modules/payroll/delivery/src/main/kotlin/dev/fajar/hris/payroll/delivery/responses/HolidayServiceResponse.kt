package dev.fajar.hris.payroll.delivery.responses

import java.time.*

data class HolidayServiceResponse(
    val continuousFrom: LocalDate,
    val assessedUntil: LocalDate,
    val convention: String,
    val wholeMonths: Int,
    val remainingDays: Int,
    val anniversaryDays: Int,
)
