package dev.fajar.hris.identity.domain.entities

import java.util.UUID

data class AppliedRoleTemplate(
    val id: UUID,
    val code: String,
    val name: String,
    val version: Long,
    val permissions: Set<String>,
)
