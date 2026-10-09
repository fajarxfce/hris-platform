package dev.fajar.hris.communications.delivery.mappers

import dev.fajar.hris.communications.delivery.responses.*
import dev.fajar.hris.communications.domain.entities.*

fun AudienceGroup.toResponse() =
    AudienceGroupResponse(
        id,
        version,
        name,
        active,
        employmentIds,
        recordedAt.toString(),
        recordedBy,
        reason,
    )

fun AudienceGroupSummary.toResponse() =
    AudienceGroupSummaryResponse(id, version, name, active, memberCount, recordedAt.toString())
