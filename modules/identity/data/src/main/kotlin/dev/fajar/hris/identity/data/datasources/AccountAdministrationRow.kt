package dev.fajar.hris.identity.data.datasources

import java.util.UUID

data class AccountAdministrationRow(
    val id: UUID,
    val email: String,
    val displayName: String,
    val active: Boolean,
    val mfaConfigured: Boolean,
    val version: Long,
    val securityVersion: Long,
    val invitationPending: Boolean,
    val platformPermissions: Set<String>,
)
