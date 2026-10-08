package dev.fajar.hris.organization.delivery.requests

import dev.fajar.hris.organization.domain.entities.UnitKind
import java.util.UUID

data class UnitRequest(
    val code: String,
    val name: String,
    val kind: UnitKind,
    val parentId: UUID? = null,
    val timezone: String? = null,
    val active: Boolean = true,
    val expectedVersion: Long? = null,
)
