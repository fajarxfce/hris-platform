package dev.fajar.hris.people.delivery.requests

import dev.fajar.hris.people.domain.entities.LifecycleTaskStatus

data class LifecycleTaskChangeRequest(
    val expectedVersion: Long,
    val status: LifecycleTaskStatus,
    val reason: String,
)
