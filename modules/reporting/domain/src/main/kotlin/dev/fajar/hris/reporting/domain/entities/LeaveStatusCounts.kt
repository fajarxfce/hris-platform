package dev.fajar.hris.reporting.domain.entities

import dev.fajar.hris.leave.domain.entities.LeaveStatus

data class LeaveStatusCounts(val status: LeaveStatus, val counts: LeaveReportCounts)
