package dev.fajar.hris.organization.delivery.responses

import java.util.UUID

data class UnitResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val kind: String,
    val parentId: UUID?,
    val timezone: String?,
    val active: Boolean,
    val version: Long,
)
