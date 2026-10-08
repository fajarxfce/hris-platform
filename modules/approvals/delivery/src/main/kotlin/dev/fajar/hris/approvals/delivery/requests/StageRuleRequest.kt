package dev.fajar.hris.approvals.delivery.requests

import dev.fajar.hris.approvals.domain.entities.AssignmentKind
import java.util.UUID

data class StageRuleRequest(
    val assignment: AssignmentKind,
    val accountIds: Set<UUID> = emptySet(),
    val permission: String? = null,
)
