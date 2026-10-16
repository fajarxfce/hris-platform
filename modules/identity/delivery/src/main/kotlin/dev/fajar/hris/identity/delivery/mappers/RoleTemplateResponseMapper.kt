package dev.fajar.hris.identity.delivery.mappers

import dev.fajar.hris.core.domain.Page
import dev.fajar.hris.identity.delivery.responses.*
import dev.fajar.hris.identity.domain.entities.*

fun CompanyRoleTemplate.toResponse() =
    RoleTemplateResponse(id, code, name, permissions, active, version)

fun RoleTemplateReview.toResponse() =
    RoleTemplateReviewResponse(
        current.toResponse(),
        Page(
            history.items.map {
                RoleTemplateRevisionResponse(
                    it.revision,
                    it.code,
                    it.name,
                    it.permissions,
                    it.active,
                    it.actorId,
                    it.reason,
                    it.recordedAt,
                )
            },
            history.nextCursor,
        ),
    )
