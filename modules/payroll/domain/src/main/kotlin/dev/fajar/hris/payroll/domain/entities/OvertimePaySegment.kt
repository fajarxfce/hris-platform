package dev.fajar.hris.payroll.domain.entities

import java.math.BigDecimal

data class OvertimePaySegment(val minutes: Int, val multiplier: BigDecimal)
