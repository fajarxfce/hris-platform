package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.people.domain.entities.*
import java.math.BigDecimal
import java.time.*

fun PayrollPayUnits.toSnapshotData() =
    PayrollPayUnitsData(
        total = total.toPlainString(),
        payable = payable.toPlainString(),
        unpaid = unpaid.toPlainString(),
        employedDates = java.util.Set.copyOf(employedDates.map { item -> item }),
    )

fun PayrollPayUnitsData.toDomain() =
    PayrollPayUnits(
        total = BigDecimal(total),
        payable = BigDecimal(payable),
        unpaid = BigDecimal(unpaid),
        employedDates = java.util.Set.copyOf(employedDates.map { item -> item }),
    )

fun ProratedPay.toSnapshotData() =
    ProratedPayData(
        monthlyAmount = monthlyAmount.toPlainString(),
        totalUnits = totalUnits.toPlainString(),
        payableUnits = payableUnits.toPlainString(),
        amount = amount.toPlainString(),
        rounding = rounding.name,
    )

fun ProratedPayData.toDomain() =
    ProratedPay(
        monthlyAmount = BigDecimal(monthlyAmount),
        totalUnits = BigDecimal(totalUnits),
        payableUnits = BigDecimal(payableUnits),
        amount = BigDecimal(amount),
        rounding = EarningsRounding.valueOf(rounding),
    )

fun PayrollEarningLine.toSnapshotData() =
    PayrollEarningLineData(
        kind = kind.name,
        code = code,
        name = name,
        amount = amount.toPlainString(),
        taxable = taxable,
        proration = proration?.let { value -> value.toSnapshotData() },
    )

fun PayrollEarningLineData.toDomain() =
    PayrollEarningLine(
        kind = PayrollEarningKind.valueOf(kind),
        code = code,
        name = name,
        amount = BigDecimal(amount),
        taxable = taxable,
        proration = proration?.let { value -> value.toDomain() },
    )

fun OvertimePaySegment.toSnapshotData() =
    OvertimePaySegmentData(minutes = minutes, multiplier = multiplier.toPlainString())

fun OvertimePaySegmentData.toDomain() =
    OvertimePaySegment(minutes = minutes, multiplier = BigDecimal(multiplier))

fun OvertimePay.toSnapshotData() =
    OvertimePayData(
        ruleId = ruleId,
        dayKind = dayKind.name,
        monthlyWage = monthlyWage.toPlainString(),
        displayHourlyWage = displayHourlyWage.toPlainString(),
        segments = java.util.List.copyOf(segments.map { item -> item.toSnapshotData() }),
        amount = amount.toPlainString(),
        rounding = rounding.name,
    )

fun OvertimePayData.toDomain() =
    OvertimePay(
        ruleId = ruleId,
        dayKind = OvertimeDayKind.valueOf(dayKind),
        monthlyWage = BigDecimal(monthlyWage),
        displayHourlyWage = BigDecimal(displayHourlyWage),
        segments = java.util.List.copyOf(segments.map { item -> item.toDomain() }),
        amount = BigDecimal(amount),
        rounding = EarningsRounding.valueOf(rounding),
    )

fun PayrollOvertimeDay.toSnapshotData() =
    PayrollOvertimeDayData(date = date, calculation = calculation.toSnapshotData())

fun PayrollOvertimeDayData.toDomain() =
    PayrollOvertimeDay(date = date, calculation = calculation.toDomain())

fun HolidayService.toSnapshotData() =
    HolidayServiceData(
        continuousFrom = continuousFrom,
        assessedUntil = assessedUntil,
        convention = convention.name,
        wholeMonths = wholeMonths,
        remainingDays = remainingDays,
        anniversaryDays = anniversaryDays,
    )

fun HolidayServiceData.toDomain() =
    HolidayService(
        continuousFrom = continuousFrom,
        assessedUntil = assessedUntil,
        convention = ServiceMonthConvention.valueOf(convention),
        wholeMonths = wholeMonths,
        remainingDays = remainingDays,
        anniversaryDays = anniversaryDays,
    )

fun HolidayAllowance.toSnapshotData() =
    HolidayAllowanceData(
        ruleId = ruleId,
        holidayDate = holidayDate,
        dueDate = dueDate,
        service = service.toSnapshotData(),
        monthlyWage = monthlyWage.toPlainString(),
        statutoryAmount = statutoryAmount.toPlainString(),
        employerTopUp = employerTopUp.toPlainString(),
        amount = amount.toPlainString(),
        rounding = rounding.name,
    )

fun HolidayAllowanceData.toDomain() =
    HolidayAllowance(
        ruleId = ruleId,
        holidayDate = holidayDate,
        dueDate = dueDate,
        service = service.toDomain(),
        monthlyWage = BigDecimal(monthlyWage),
        statutoryAmount = BigDecimal(statutoryAmount),
        employerTopUp = BigDecimal(employerTopUp),
        amount = BigDecimal(amount),
        rounding = EarningsRounding.valueOf(rounding),
    )
