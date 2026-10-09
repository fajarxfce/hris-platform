package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.*

fun PayrollInputTerms.toResponse() =
    PayrollInputTermsResponse(
        earnings.map {
            PayrollVariableEarningResponse(it.code, it.name, it.amount.toPlainString(), it.taxable)
        },
        deductions.map { PayrollNetDeductionResponse(it.code, it.name, it.amount.toPlainString()) },
        nonCashTaxable.toPlainString(),
        scheduledMonthUnits?.toPlainString(),
        dayResolutions.map {
            PayrollDayResolutionResponse(
                it.workDate.toString(),
                it.portion.name,
                it.disposition.name,
                it.reference,
            )
        },
        holidayAllowance?.let {
            PayrollHolidayInputResponse(
                it.kind.name,
                it.holidayDate.toString(),
                it.continuousServiceFrom.toString(),
                it.priorPayment.name,
                it.reviewReference,
                it.promisedAmount?.toPlainString(),
            )
        },
        reviewReference,
    )

fun PayrollInput.toResponse() =
    PayrollInputResponse(
        id,
        employeeId,
        earningsMonth.toString(),
        version,
        workJobId,
        workPeriodVersion,
        employmentVersion,
        terms.toResponse(),
        status.name,
        preparedBy,
        verifiedBy,
        recordedAt,
        reason,
    )

fun PayrollInputSummary.toResponse() =
    PayrollInputSummaryResponse(
        id,
        employeeId,
        earningsMonth.toString(),
        version,
        status.name,
        preparedBy,
        verifiedBy,
        recordedAt,
        reason,
    )

fun PayrollWorkSource.toResponse() =
    PayrollWorkSourceResponse(
        periodId,
        earningsMonth.toString(),
        version,
        closed,
        jobId,
        includesEmployee,
    )
