package dev.fajar.hris.leave.delivery.mappers

import dev.fajar.hris.core.domain.*
import dev.fajar.hris.core.http.*
import java.math.BigDecimal

fun parseHalfDays(value: String): Int {
    val days = decimalAmount(value)
    val halves = days.multiply(BigDecimal.TWO)
    if (days.abs() > BigDecimal(366) || halves.remainder(BigDecimal.ONE).signum() != 0)
        throw DomainFailureException(Failure(FailureKind.VALIDATION, "invalid_leave_quantity"))
    return halves.intValueExact()
}

fun Int.toLeaveDays(): String = BigDecimal.valueOf(toLong()).divide(BigDecimal.TWO).toPlainString()
