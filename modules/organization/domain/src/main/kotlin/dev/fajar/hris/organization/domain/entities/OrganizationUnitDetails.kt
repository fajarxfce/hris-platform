package dev.fajar.hris.organization.domain.entities

import java.util.UUID

data class OrganizationUnitDetails(
    val companyId: UUID,
    val unit: OrganizationUnit,
    val parent: OrganizationUnit?,
)
