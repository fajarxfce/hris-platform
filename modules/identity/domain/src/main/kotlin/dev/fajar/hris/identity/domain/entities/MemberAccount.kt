package dev.fajar.hris.identity.domain.entities

import java.util.UUID

data class MemberAccount(
    val id: UUID,
    val email: String,
    val displayName: String,
    val accountActive: Boolean,
    val membershipActive: Boolean,
    val permissions: Set<String>,
    val version: Long,
)
