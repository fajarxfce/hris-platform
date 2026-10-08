package dev.fajar.hris.identity.delivery.requests

import java.util.UUID

data class AccountInvitationRequest(
    val id: UUID,
    val email: String,
    val displayName: String,
    val reason: String,
    val expectedVersion: Long? = null,
)
