package dev.fajar.hris.people.delivery.responses

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LifecycleCaseResponse(
    val id: UUID,
    val employmentId: UUID,
    val kind: String,
    val targetDate: LocalDate,
    val templateId: UUID,
    val templateVersion: Long,
    val templateName: String,
    val status: String,
    val version: Long,
    val createdBy: UUID,
    val createdAt: Instant,
    val tasks: List<LifecycleTaskResponse>,
    val employee: LifecycleEmployeeResponse,
)
