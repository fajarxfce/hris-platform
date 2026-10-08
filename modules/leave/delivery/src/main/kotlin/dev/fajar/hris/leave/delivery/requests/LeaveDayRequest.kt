package dev.fajar.hris.leave.delivery.requests

import dev.fajar.hris.leave.domain.entities.LeavePortion
import java.time.LocalDate

data class LeaveDayRequest(val workDate: LocalDate, val portion: LeavePortion = LeavePortion.FULL)
