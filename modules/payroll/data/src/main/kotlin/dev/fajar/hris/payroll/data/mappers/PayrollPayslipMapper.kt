package dev.fajar.hris.payroll.data.mappers

import dev.fajar.hris.payroll.data.models.*
import dev.fajar.hris.payroll.domain.entities.*
import java.time.YearMonth
import tools.jackson.databind.ObjectMapper

fun PayrollPayslipSummaryRow.toPayslipSummary() =
    PayrollPayslipSummary(
        id,
        employeeId,
        employeeNumber,
        employeeName,
        YearMonth.from(taxMonth),
        plannedPaymentDate,
        taxableGross,
        withheld,
        takeHome,
        publishedAt.toInstant(),
    )

fun PayrollPayslipRow.toPayslip(json: ObjectMapper) =
    PayrollPayslip(
        summary.toPayslipSummary(),
        companyCode,
        companyName,
        json.readValue(calculation, PayrollMonthlyCalculationData::class.java).toDomain(),
    )
