package dev.fajar.hris.approvals.domain.entities

import java.util.UUID

data class StageRule(
    val assignment: AssignmentKind,
    val accountIds: Set<UUID> = emptySet(),
    val permission: String? = null,
)
