package dev.fajar.hris.organization.domain.entities

import java.util.UUID

data class Company(
    val id: UUID,
    val code: String,
    val name: String,
    val timezone: String,
    val active: Boolean,
    val version: Long,
)
