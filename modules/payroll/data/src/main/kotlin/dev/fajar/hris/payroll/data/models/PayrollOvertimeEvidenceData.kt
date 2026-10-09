package dev.fajar.hris.payroll.data.models

import java.time.*
import java.util.UUID

data class PayrollOvertimeEvidenceData(val requestId: UUID, val revision: Long, val minutes: Int)
