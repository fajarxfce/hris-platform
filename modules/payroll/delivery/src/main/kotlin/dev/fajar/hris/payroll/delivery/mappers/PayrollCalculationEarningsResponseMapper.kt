package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.people.domain.entities.*
import java.time.*

fun PayrollPayUnits.toCalculationResponse() =
    PayrollPayUnitsResponse(
        total = total.toPlainString(),
        payable = payable.toPlainString(),
        unpaid = unpaid.toPlainString(),
        employedDates = java.util.Set.copyOf(employedDates.map { item -> item }),
    )

fun ProratedPay.toCalculationResponse() =
    ProratedPayResponse(
        monthlyAmount = monthlyAmount.toPlainString(),
        totalUnits = totalUnits.toPlainString(),
        payableUnits = payableUnits.toPlainString(),
        amount = amount.toPlainString(),
        rounding = rounding.name,
    )

fun PayrollEarningLine.toCalculationResponse() =
    PayrollEarningLineResponse(
        kind = kind.name,
        code = code,
        name = name,
        amount = amount.toPlainString(),
        taxable = taxable,
        proration = proration?.let { value -> value.toCalculationResponse() },
    )

fun OvertimePaySegment.toCalculationResponse() =
    OvertimePaySegmentResponse(minutes = minutes, multiplier = multiplier.toPlainString())

fun OvertimePay.toCalculationResponse() =
    OvertimePayResponse(
        ruleId = ruleId,
        dayKind = dayKind.name,
        monthlyWage = monthlyWage.toPlainString(),
        displayHourlyWage = displayHourlyWage.toPlainString(),
        segments = java.util.List.copyOf(segments.map { item -> item.toCalculationResponse() }),
        amount = amount.toPlainString(),
        rounding = rounding.name,
    )

fun PayrollOvertimeDay.toCalculationResponse() =
    PayrollOvertimeDayResponse(date = date, calculation = calculation.toCalculationResponse())

fun HolidayService.toCalculationResponse() =
    HolidayServiceResponse(
        continuousFrom = continuousFrom,
        assessedUntil = assessedUntil,
        convention = convention.name,
        wholeMonths = wholeMonths,
        remainingDays = remainingDays,
        anniversaryDays = anniversaryDays,
    )

fun HolidayAllowance.toCalculationResponse() =
    HolidayAllowanceResponse(
        ruleId = ruleId,
        holidayDate = holidayDate,
        dueDate = dueDate,
        service = service.toCalculationResponse(),
        monthlyWage = monthlyWage.toPlainString(),
        statutoryAmount = statutoryAmount.toPlainString(),
        employerTopUp = employerTopUp.toPlainString(),
        amount = amount.toPlainString(),
        rounding = rounding.name,
    )
