package dev.fajar.hris.identity.delivery.responses

import java.util.UUID

data class MemberResponse(
    val id: UUID,
    val email: String,
    val displayName: String,
    val accountActive: Boolean,
    val active: Boolean,
    val permissions: Set<String>,
    val version: Long,
)
