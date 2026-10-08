package dev.fajar.hris.identity.delivery.responses

import java.util.UUID

data class AppliedRoleTemplateResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val permissions: Set<String>,
    val version: Long,
)
