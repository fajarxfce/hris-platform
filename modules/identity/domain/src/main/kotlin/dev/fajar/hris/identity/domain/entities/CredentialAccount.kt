package dev.fajar.hris.identity.domain.entities

import java.util.UUID

data class CredentialAccount(
    val id: UUID,
    val email: String,
    val displayName: String,
    val active: Boolean,
    val invitationPending: Boolean,
    val hasPassword: Boolean,
    val version: Long,
    val securityVersion: Long,
)
