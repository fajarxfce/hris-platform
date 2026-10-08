package dev.fajar.hris.people.domain.entities

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LifecycleTask(
    val key: String,
    val title: String,
    val required: Boolean,
    val dueDate: LocalDate,
    val assigneeId: UUID?,
    val status: LifecycleTaskStatus,
    val completedBy: UUID?,
    val completedAt: Instant?,
)
