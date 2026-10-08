package dev.fajar.hris.identity.delivery.responses

import java.util.UUID

data class RoleTemplateResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val permissions: Set<String>,
    val active: Boolean,
    val version: Long,
)
