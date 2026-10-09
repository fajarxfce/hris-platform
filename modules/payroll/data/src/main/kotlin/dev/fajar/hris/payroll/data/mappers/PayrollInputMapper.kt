package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.payroll.domain.entities.*
import dev.fajar.hris.schema.tables.records.PayrollInputRevisionsRecord
import java.math.BigDecimal
import java.time.*
import tools.jackson.databind.ObjectMapper

fun PayrollInputTerms.toData() =
    PayrollInputTermsData(
        earnings.map {
            PayrollVariableEarningData(it.code, it.name, it.amount.toPlainString(), it.taxable)
        },
        deductions.map { PayrollNetDeductionData(it.code, it.name, it.amount.toPlainString()) },
        nonCashTaxable.toPlainString(),
        scheduledMonthUnits?.toPlainString(),
        dayResolutions.map {
            PayrollDayResolutionData(
                it.workDate.toString(),
                it.portion.name,
                it.disposition.name,
                it.reference,
            )
        },
        holidayAllowance?.let {
            PayrollHolidayInputData(
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

fun PayrollInputTermsData.toTerms() =
    PayrollInputTerms(
        java.util.List.copyOf(
            earnings.map {
                PayrollVariableEarning(it.code, it.name, BigDecimal(it.amount), it.taxable)
            }
        ),
        java.util.List.copyOf(
            deductions.map { PayrollNetDeduction(it.code, it.name, BigDecimal(it.amount)) }
        ),
        BigDecimal(nonCashTaxable),
        scheduledMonthUnits?.let(::BigDecimal),
        java.util.List.copyOf(
            dayResolutions.map {
                PayrollDayResolution(
                    LocalDate.parse(it.workDate),
                    PayrollDayPortion.valueOf(it.portion),
                    PayrollDayDisposition.valueOf(it.disposition),
                    it.reference,
                )
            }
        ),
        holidayAllowance?.let {
            PayrollHolidayInput(
                PayrollHolidayKind.valueOf(it.kind),
                LocalDate.parse(it.holidayDate),
                LocalDate.parse(it.continuousServiceFrom),
                PayrollPriorHolidayPayment.valueOf(it.priorPayment),
                it.reviewReference,
                it.promisedAmount?.let(::BigDecimal),
            )
        },
        reviewReference,
    )

fun PayrollInputRevisionsRecord.toInput(json: ObjectMapper) =
    PayrollInput(
        inputId,
        employmentId,
        YearMonth.from(earningsMonth),
        revision,
        workJobId,
        workPeriodVersion,
        employmentVersion,
        json.readValue(terms.data(), PayrollInputTermsData::class.java).toTerms(),
        PayrollInputStatus.valueOf(status),
        preparedBy,
        verifiedBy,
        recordedAt.toInstant(),
        reason,
    )

fun PayrollInputSummaryRow.toSummary() =
    PayrollInputSummary(
        id,
        employeeId,
        YearMonth.from(month),
        version,
        PayrollInputStatus.valueOf(status),
        preparedBy,
        verifiedBy,
        recordedAt.toInstant(),
        reason,
    )
