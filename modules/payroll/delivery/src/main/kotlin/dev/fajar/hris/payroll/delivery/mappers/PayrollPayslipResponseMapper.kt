package dev.fajar.hris.payroll.delivery.mappers

import dev.fajar.hris.payroll.delivery.responses.*
import dev.fajar.hris.payroll.domain.entities.*

fun PayrollPayslipSummary.toResponse() =
    PayrollPayslipSummaryResponse(
        id,
        employeeId,
        employeeNumber,
        employeeName,
        taxMonth,
        plannedPaymentDate,
        currency,
        taxableGross.toPlainString(),
        withheld.toPlainString(),
        takeHome.toPlainString(),
        publishedAt,
        version,
    )

fun PayrollPayslip.toResponse() =
    PayrollPayslipResponse(
        summary.toResponse(),
        companyCode,
        companyName,
        calculation.toCalculationResponse(),
    )
