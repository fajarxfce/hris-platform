package dev.fajar.hris.approvals.delivery.responses

import java.util.UUID

data class StageRuleResponse(
    val assignment: String,
    val accountIds: Set<UUID>,
    val permission: String?,
)
