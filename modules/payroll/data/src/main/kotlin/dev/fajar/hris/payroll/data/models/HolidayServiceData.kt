package dev.fajar.hris.payroll.data.models

import java.time.*

data class HolidayServiceData(
    val continuousFrom: LocalDate,
    val assessedUntil: LocalDate,
    val convention: String,
    val wholeMonths: Int,
    val remainingDays: Int,
    val anniversaryDays: Int,
)
