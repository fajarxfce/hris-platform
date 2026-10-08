package dev.fajar.hris.approvals.data.models

import java.util.UUID

data class StageRuleData(val assignment: String, val accountIds: Set<UUID>, val permission: String?)
