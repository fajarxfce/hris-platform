package dev.fajar.hris.identity.domain.entities

import dev.fajar.hris.core.domain.Page

data class RoleTemplateReview(
    val current: CompanyRoleTemplate,
    val history: Page<RoleTemplateRevision>,
)
