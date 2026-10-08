package dev.fajar.hris.identity.data.datasources

import java.util.UUID

data class CredentialAccountRow(
    val id: UUID,
    val email: String,
    val displayName: String,
    val active: Boolean,
    val invitationPending: Boolean,
    val hasPassword: Boolean,
    val version: Long,
    val securityVersion: Long,
)
