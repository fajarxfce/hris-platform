package dev.fajar.hris.people.domain.entities

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class LifecycleCase(
    val id: UUID,
    val employmentId: UUID,
    val kind: LifecycleKind,
    val targetDate: LocalDate,
    val templateId: UUID,
    val templateVersion: Long,
    val templateName: String,
    val status: LifecycleStatus,
    val version: Long,
    val createdBy: UUID,
    val createdAt: Instant,
    val tasks: List<LifecycleTask>,
)
