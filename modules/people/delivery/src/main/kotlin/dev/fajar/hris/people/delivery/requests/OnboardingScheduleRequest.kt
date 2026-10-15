package dev.fajar.hris.people.delivery.requests

import java.time.LocalDate
import java.util.UUID

data class OnboardingScheduleRequest(
    val id: UUID,
    val caseId: UUID,
    val templateId: UUID,
    val employmentVersion: Long,
    val templateVersion: Long,
    val basisRevision: String,
    val targetDate: LocalDate,
    val startsOn: LocalDate,
    val assignees: Map<String, UUID>,
    val reason: String,
)
