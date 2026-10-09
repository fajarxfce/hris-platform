package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.core.http.decimalAmount
import dev.fajar.hris.payroll.delivery.requests.*
import dev.fajar.hris.payroll.domain.entities.*

fun PayrollInputTermsRequest.toTerms() =
    PayrollInputTerms(
        java.util.List.copyOf(
            earnings.map {
                PayrollVariableEarning(it.code, it.name, decimalAmount(it.amount), it.taxable)
            }
        ),
        java.util.List.copyOf(
            deductions.map { PayrollNetDeduction(it.code, it.name, decimalAmount(it.amount)) }
        ),
        decimalAmount(nonCashTaxable),
        scheduledMonthUnits?.let(::decimalAmount),
        java.util.List.copyOf(
            dayResolutions.map {
                PayrollDayResolution(it.workDate, it.portion, it.disposition, it.reference)
            }
        ),
        holidayAllowance?.let {
            PayrollHolidayInput(
                it.kind,
                it.holidayDate,
                it.continuousServiceFrom,
                it.priorPayment,
                it.reviewReference,
                it.promisedAmount?.let(::decimalAmount),
            )
        },
        reviewReference,
    )
