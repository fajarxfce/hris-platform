package dev.fajar.hris.organization.delivery.responses

import java.util.UUID

data class CompanyResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val timezone: String,
    val active: Boolean,
    val version: Long,
)
