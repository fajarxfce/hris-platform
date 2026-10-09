package dev.fajar.hris.communications.data.mappers

import dev.fajar.hris.communications.data.models.AudienceGroupSummaryRow
import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.schema.tables.records.AudienceGroupRevisionsRecord
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

fun AudienceGroupRevisionsRecord.toGroup(json: ObjectMapper) =
    AudienceGroup(
        id,
        version,
        name,
        active,
        decodeAudienceIds(memberIds, json, 5000).toSet(),
        recordedAt.toInstant(),
        actorId,
        reason,
    )

fun AudienceGroupSummaryRow.toSummary() =
    AudienceGroupSummary(id, version, name, active, memberCount, recordedAt.toInstant())

fun AudienceGroup.toRecord(company: UUID, json: ObjectMapper) =
    AudienceGroupRevisionsRecord().also {
        it.companyId = company
        it.id = id
        it.version = version
        it.name = name
        it.active = active
        it.memberIds =
            JSONB.valueOf(json.writeValueAsString(employmentIds.map(UUID::toString).sorted()))
        it.recordedAt = recordedAt.atOffset(ZoneOffset.UTC)
        it.actorId = recordedBy
        it.reason = reason
    }
