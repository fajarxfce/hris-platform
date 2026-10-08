package dev.fajar.hris.people.delivery.requests

import java.util.UUID

data class LifecycleAssignmentRequest(
    val expectedVersion: Long,
    val assigneeId: UUID?,
    val reason: String,
)
