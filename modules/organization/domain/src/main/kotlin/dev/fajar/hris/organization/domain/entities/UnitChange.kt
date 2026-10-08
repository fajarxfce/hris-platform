package dev.fajar.hris.organization.domain.entities

import java.util.UUID

data class UnitChange(
    val id: UUID,
    val code: String,
    val name: String,
    val kind: UnitKind,
    val parentId: UUID?,
    val timezone: String?,
    val active: Boolean,
    val expectedVersion: Long?,
)
