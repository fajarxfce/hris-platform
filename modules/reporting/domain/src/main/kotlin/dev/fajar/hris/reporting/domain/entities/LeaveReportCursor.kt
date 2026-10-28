package dev.fajar.hris.reporting.domain.entities

import java.time.Instant
import java.util.UUID

data class LeaveReportCursor(val submittedAt: Instant, val id: UUID)
