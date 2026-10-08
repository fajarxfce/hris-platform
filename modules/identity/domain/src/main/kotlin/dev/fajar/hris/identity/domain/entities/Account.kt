package dev.fajar.hris.identity.domain.entities

import java.util.UUID

data class Account(
    val id: UUID,
    val email: String,
    val displayName: String,
    val active: Boolean,
    val mfaConfigured: Boolean,
    val version: Long,
    val securityVersion: Long = 0,
)
