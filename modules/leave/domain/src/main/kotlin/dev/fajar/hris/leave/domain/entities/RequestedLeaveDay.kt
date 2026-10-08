package dev.fajar.hris.leave.domain.entities

import java.time.LocalDate

data class RequestedLeaveDay(val workDate: LocalDate, val portion: LeavePortion)
