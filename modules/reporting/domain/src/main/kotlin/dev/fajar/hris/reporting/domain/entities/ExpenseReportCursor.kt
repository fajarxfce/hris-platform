package dev.fajar.hris.reporting.domain.entities

import java.time.Instant
import java.util.UUID

data class ExpenseReportCursor(val submittedAt: Instant, val id: UUID)
