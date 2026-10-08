package dev.fajar.hris.approvals.delivery.responses

import java.util.UUID

data class ApprovalStageResponse(val assignees: Set<UUID>)
