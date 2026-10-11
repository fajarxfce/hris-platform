package dev.fajar.hris.communications.delivery.responses

import java.util.UUID

data class AudienceReferenceResponse(
    val id: UUID,
    val kind: String,
    val name: String,
    val code: String?,
    val version: Long,
    val active: Boolean?,
)
