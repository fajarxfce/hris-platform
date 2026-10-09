package dev.fajar.hris.payroll.delivery.responses

import java.time.*

data class OvertimePaySegmentResponse(val minutes: Int, val multiplier: String)
