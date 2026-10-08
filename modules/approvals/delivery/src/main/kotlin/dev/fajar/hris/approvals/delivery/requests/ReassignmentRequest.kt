package dev.fajar.hris.approvals.delivery.requests

import java.util.UUID

data class ReassignmentRequest(val version: Long, val assignees: Set<UUID>, val reason: String)
