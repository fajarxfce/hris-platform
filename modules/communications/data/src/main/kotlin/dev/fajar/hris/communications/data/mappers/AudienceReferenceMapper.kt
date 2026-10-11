package dev.fajar.hris.communications.data.mappers

import dev.fajar.hris.communications.data.models.AudienceReferenceRow
import dev.fajar.hris.communications.domain.entities.*

fun AudienceReferenceRow.toReference(kind: AudienceReferenceKind): AudienceReference =
    AudienceReference(id, kind, name, code, version, active)
