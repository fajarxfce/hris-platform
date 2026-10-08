package dev.fajar.hris.identity.delivery.responses

import java.util.UUID

data class ManagedAccountResponse(
    val id: UUID,
    val email: String,
    val displayName: String,
    val active: Boolean,
    val invitationPending: Boolean,
    val mfaConfigured: Boolean,
    val platformPermissions: Set<String>,
    val version: Long,
)
