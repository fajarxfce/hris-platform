package dev.fajar.hris.identity.data.datasources

import java.util.UUID

data class MemberRow(
    val id: UUID,
    val email: String,
    val displayName: String,
    val accountActive: Boolean,
    val membershipActive: Boolean,
    val permissions: Set<String>,
    val version: Long,
)
