package dev.fajar.hris.leave.domain.entities

import java.time.LocalDate

data class LeaveOccupancy(val workDate: LocalDate, val mask: Int)
