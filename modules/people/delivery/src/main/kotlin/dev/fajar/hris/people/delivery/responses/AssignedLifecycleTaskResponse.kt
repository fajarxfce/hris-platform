package dev.fajar.hris.people.delivery.responses

import java.util.UUID

data class AssignedLifecycleTaskResponse(
    val caseId: UUID,
    val employmentId: UUID,
    val kind: String,
    val caseVersion: Long,
    val task: LifecycleTaskResponse,
)
