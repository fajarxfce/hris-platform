package dev.fajar.hris.reporting.delivery.mappers

import dev.fajar.hris.reporting.delivery.responses.OvertimeReportCountsResponse
import dev.fajar.hris.reporting.domain.entities.OvertimeReportCounts

fun OvertimeReportCounts.toResponse() =
    OvertimeReportCountsResponse(
        requests,
        employments,
        actualRequests,
        requestedMinutes,
        actualMinutes,
        approvedMinutes,
    )
