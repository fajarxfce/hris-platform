package dev.fajar.hris.people.domain.entities

import java.util.UUID

data class ScheduleOffboardingCommand(
    val id: UUID,
    val caseId: UUID,
    val expectedVersion: Long,
    val employmentVersion: Long,
    val basisRevision: String,
    val reason: String,
)
