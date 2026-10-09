package dev.fajar.hris.payroll.domain.entities

import java.util.UUID

data class PayrollOvertimeEvidence(val requestId: UUID, val revision: Long, val minutes: Int)
