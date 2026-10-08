package dev.fajar.hris.people.delivery.responses

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LifecycleTaskResponse(
    val key: String,
    val title: String,
    val required: Boolean,
    val dueDate: LocalDate,
    val assigneeId: UUID?,
    val status: String,
    val completedBy: UUID?,
    val completedAt: Instant?,
)
