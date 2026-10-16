package dev.fajar.hris.identity.delivery.responses

import dev.fajar.hris.core.domain.Page

data class RoleTemplateReviewResponse(
    val current: RoleTemplateResponse,
    val history: Page<RoleTemplateRevisionResponse>,
)
