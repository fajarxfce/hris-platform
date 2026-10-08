package dev.fajar.hris.approvals.domain.entities

import java.util.UUID

data class ApprovalStage(val assignees: Set<UUID>)
