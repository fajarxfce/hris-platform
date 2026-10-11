package dev.fajar.hris.communications.domain.entities

import java.util.UUID

data class AudienceReference(
    val id: UUID,
    val kind: AudienceReferenceKind,
    val name: String,
    val code: String?,
    val version: Long,
    // Employment membership does not imply current publication eligibility.
    val active: Boolean?,
)
