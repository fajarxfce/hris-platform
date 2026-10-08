package dev.fajar.hris.people.domain.entities

import java.util.UUID

data class AssignedLifecycleTask(
    val caseId: UUID,
    val employmentId: UUID,
    val kind: LifecycleKind,
    val caseVersion: Long,
    val task: LifecycleTask,
)
