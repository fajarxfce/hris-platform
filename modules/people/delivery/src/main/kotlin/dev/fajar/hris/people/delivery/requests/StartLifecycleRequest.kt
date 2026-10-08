package dev.fajar.hris.people.delivery.requests

import java.time.LocalDate
import java.util.UUID

data class StartLifecycleRequest(
    val id: UUID,
    val employmentId: UUID,
    val templateId: UUID,
    val templateVersion: Long,
    val targetDate: LocalDate,
    val assignees: Map<String, UUID> = emptyMap(),
    val reason: String,
)
