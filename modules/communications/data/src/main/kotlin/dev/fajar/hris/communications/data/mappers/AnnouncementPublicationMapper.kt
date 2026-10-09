package dev.fajar.hris.communications.data.mappers

import dev.fajar.hris.communications.data.models.*
import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.schema.tables.records.AnnouncementPublicationsRecord
import java.time.ZoneOffset
import java.util.UUID
import org.jooq.JSONB
import tools.jackson.databind.ObjectMapper

fun AnnouncementRecipientSelection.toQuery(companyId: UUID) =
    AnnouncementRecipientQuery(
        companyId,
        date,
        audience.kind.name,
        audience.targetIds,
        employmentStatuses.map { it.name }.toSet(),
        accountActive,
        membershipActive,
        requiredPermission,
    )

fun AnnouncementRecipientRow.toRecipient() = AnnouncementRecipient(accountId, employmentId)

fun AnnouncementPublication.toRecord(companyId: UUID, json: ObjectMapper) =
    AnnouncementPublicationsRecord().also {
        require(
            recipients.size in 1..5000 &&
                recipients.map { r -> r.accountId }.toSet().size == recipients.size
        )
        it.companyId = companyId
        it.id = id
        it.announcementId = announcementId
        it.contentVersion = contentVersion
        it.publishedAt = publishedAt.atOffset(ZoneOffset.UTC)
        it.actorId = publishedBy
        it.recipients =
            JSONB.valueOf(
                json.writeValueAsString(
                    recipients.associate { r ->
                        r.accountId.toString() to r.employmentId.toString()
                    }
                )
            )
        it.recipientCount = recipients.size
        it.audienceVersions =
            JSONB.valueOf(
                json.writeValueAsString(audienceVersions.mapKeys { entry -> entry.key.toString() })
            )
    }
