package dev.fajar.hris.administration.delivery.responses

import java.util.UUID

data class PlatformAccountAccessAuditResponse(
    val id: UUID,
    val accountId: UUID,
    val active: Boolean,
    val permissions: List<String>,
    val reason: String?,
)
