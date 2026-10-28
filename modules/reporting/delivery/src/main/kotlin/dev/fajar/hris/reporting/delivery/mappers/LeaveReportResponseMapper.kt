package dev.fajar.hris.reporting.delivery.mappers

import dev.fajar.hris.reporting.delivery.responses.*
import dev.fajar.hris.reporting.domain.entities.*
import java.math.BigDecimal

fun LeaveReportCounts.toResponse() =
    LeaveReportCountsResponse(requests, employments, halfDays.toReportDays())

fun LeaveReportRequest.toResponse() =
    LeaveReportRequestResponse(
        id,
        employeeId,
        employeeNumber,
        employeeName,
        typeCode,
        typeName,
        status.name,
        submittedAt,
        version,
        halfDays.toReportDays(),
        requestHalfDays.toReportDays(),
    )

private fun Long.toReportDays(): String =
    BigDecimal.valueOf(this).divide(BigDecimal.TWO).toPlainString()
