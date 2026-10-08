package dev.fajar.hris.people.domain.entities

import java.time.LocalDate
import java.util.UUID

data class StartLifecycleCommand(
    val employmentId: UUID,
    val templateId: UUID,
    val templateVersion: Long,
    val targetDate: LocalDate,
    val assignees: Map<String, UUID>,
    val reason: String,
)
