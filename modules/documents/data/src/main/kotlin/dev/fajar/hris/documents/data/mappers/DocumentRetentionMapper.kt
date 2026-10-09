package dev.fajar.hris.documents.data.mappers

import dev.fajar.hris.documents.domain.entities.*
import dev.fajar.hris.schema.tables.records.*

fun DocumentRetentionPolicyViewsRecord.toRetentionPolicy() =
    DocumentRetentionPolicy(
        id,
        DocumentClassification.valueOf(classification),
        version,
        retentionDays,
        actorId,
        recordedAt.toInstant(),
        reason,
    )

fun DocumentRetentionStateViewsRecord.toRetentionState() =
    DocumentRetentionState(
        documentId,
        version,
        archivedAt?.let {
            DocumentArchive(
                it.toInstant(),
                policyId,
                policyVersion,
                retentionDays,
                eligibleAt?.toInstant(),
            )
        },
        legalHold,
    )

fun DocumentRetentionStateViewsRecord.toRetentionChange() =
    DocumentRetentionChange(
        toRetentionState(),
        DocumentRetentionAction.valueOf(kind),
        actorId,
        recordedAt.toInstant(),
        reason,
        retiredRevisionId,
    )
