package dev.fajar.hris.payroll.domain.entities

import java.time.LocalDate

data class HolidayService(
    val continuousFrom: LocalDate,
    val assessedUntil: LocalDate,
    val convention: ServiceMonthConvention,
    val wholeMonths: Int,
    val remainingDays: Int,
    val anniversaryDays: Int,
)
