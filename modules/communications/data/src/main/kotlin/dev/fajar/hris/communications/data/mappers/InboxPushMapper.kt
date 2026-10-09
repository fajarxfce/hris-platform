package dev.fajar.hris.communications.data.mappers

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.schema.tables.records.InboxPushDispatchesRecord

fun InboxPushDispatchesRecord.toInboxPushDispatch() =
    InboxPushDispatch(
        companyId,
        inboxId,
        accountId,
        publishedAt.toInstant(),
        enqueuedAt.toInstant(),
        InboxPushState.valueOf(state),
        availableAt.toInstant(),
        cursorSessionId,
        targetSessionId,
        processedCount,
        acceptedCount,
        rejectedCount,
        attempts,
    )

fun InboxPushDispatchesRecord.toInboxPushLease() =
    InboxPushLease(
        toInboxPushDispatch(),
        requireNotNull(leaseOwner),
        requireNotNull(leaseToken),
        requireNotNull(leaseUntil).toInstant(),
    )
