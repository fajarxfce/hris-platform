package dev.fajar.hris.communications.data.mappers

import dev.fajar.hris.communications.data.models.AnnouncementSummaryRow
import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.schema.tables.records.AnnouncementRevisionsRecord
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

fun AnnouncementRevisionsRecord.toAnnouncement(json: ObjectMapper) =
    Announcement(
        id,
        version,
        title,
        body,
        AnnouncementAudience(
            AudienceKind.valueOf(audienceKind),
            decodeAudienceIds(targetIds, json, 32),
        ),
        acknowledgementRequired,
        AnnouncementStatus.valueOf(status),
        recordedAt.toInstant(),
        actorId,
        reason,
    )

fun AnnouncementSummaryRow.toSummary() =
    AnnouncementSummary(
        id,
        version,
        title,
        AudienceKind.valueOf(audienceKind),
        targetCount,
        acknowledgementRequired,
        AnnouncementStatus.valueOf(status),
        recordedAt.toInstant(),
    )

fun Announcement.toRecord(company: UUID, json: ObjectMapper) =
    AnnouncementRevisionsRecord().also {
        it.companyId = company
        it.id = id
        it.version = version
        it.title = title
        it.body = body
        it.audienceKind = audience.kind.name
        it.targetIds =
            JSONB.valueOf(json.writeValueAsString(audience.targetIds.map(UUID::toString).sorted()))
        it.acknowledgementRequired = acknowledgementRequired
        it.status = status.name
        it.recordedAt = recordedAt.atOffset(ZoneOffset.UTC)
        it.actorId = recordedBy
        it.reason = reason
    }
