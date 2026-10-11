package dev.fajar.hris.communications.data.models

import java.util.UUID

data class AudienceReferenceRow(
    val id: UUID,
    val name: String,
    val code: String?,
    val version: Long,
    val active: Boolean?,
)
