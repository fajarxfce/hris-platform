package dev.fajar.hris.workforce.data.models

import java.time.OffsetDateTime
import java.util.UUID

data class WorkPeriodAttemptRow(
    val id: UUID,
    val actorId: UUID,
    val kind: String,
    val status: String,
    val totalItems: Int,
    val completedItems: Int,
    val cancellationRequested: Boolean,
    val failureCode: String?,
    val createdAt: OffsetDateTime,
    val finishedAt: OffsetDateTime?,
)
