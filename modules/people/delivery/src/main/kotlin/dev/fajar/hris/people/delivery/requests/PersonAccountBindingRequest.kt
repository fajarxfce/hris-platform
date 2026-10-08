package dev.fajar.hris.people.delivery.requests

import java.util.UUID

data class PersonAccountBindingRequest(
    val accountId: UUID,
    val expectedVersion: Long,
    val reason: String,
)
