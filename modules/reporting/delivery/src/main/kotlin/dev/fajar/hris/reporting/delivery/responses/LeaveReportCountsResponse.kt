package dev.fajar.hris.reporting.delivery.responses

data class LeaveReportCountsResponse(val requests: Long, val employments: Long, val days: String)
