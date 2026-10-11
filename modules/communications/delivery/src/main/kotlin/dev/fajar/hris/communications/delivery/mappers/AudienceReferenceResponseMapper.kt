package dev.fajar.hris.communications.delivery.mappers

import dev.fajar.hris.communications.delivery.responses.AudienceReferenceResponse
import dev.fajar.hris.communications.domain.entities.AudienceReference

fun AudienceReference.toResponse() =
    AudienceReferenceResponse(id, kind.name, name, code, version, active)
