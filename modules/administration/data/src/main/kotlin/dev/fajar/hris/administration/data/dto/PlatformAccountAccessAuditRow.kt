package dev.fajar.hris.administration.data.dto

import java.util.UUID

data class PlatformAccountAccessAuditRow(
    val id: UUID,
    val accountId: UUID,
    val active: Boolean,
    val permissions: List<String>,
    val reason: String?,
)
