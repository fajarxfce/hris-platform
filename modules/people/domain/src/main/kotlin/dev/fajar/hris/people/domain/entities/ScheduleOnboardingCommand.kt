package dev.fajar.hris.people.domain.entities

import java.util.UUID

data class ScheduleOnboardingCommand(
    val id: UUID,
    val caseId: UUID,
    val employmentId: UUID,
    val selection: OnboardingScheduleSelection,
    val employmentVersion: Long,
    val templateVersion: Long,
    val basisRevision: String,
    val assignees: Map<String, UUID>,
    val reason: String,
)
