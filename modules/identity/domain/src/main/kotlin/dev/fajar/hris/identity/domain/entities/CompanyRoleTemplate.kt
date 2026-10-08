package dev.fajar.hris.identity.domain.entities

import java.util.UUID

data class CompanyRoleTemplate(
    val id: UUID,
    val companyId: UUID,
    val code: String,
    val name: String,
    val permissions: Set<String>,
    val active: Boolean,
    val version: Long,
)
