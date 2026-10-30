package dev.fajar.hris.communications.data.mappers

import dev.fajar.hris.communications.domain.entities.*
import dev.fajar.hris.schema.tables.records.InboxEmailDispatchesRecord

fun InboxEmailDispatchesRecord.toInboxEmailDispatch() =
    InboxEmailDispatch(
        companyId,
        inboxId,
        accountId,
        publishedAt.toInstant(),
        enqueuedAt.toInstant(),
        InboxEmailState.valueOf(state),
        availableAt.toInstant(),
        attempts,
    )

fun InboxEmailDispatchesRecord.toInboxEmailLease() =
    InboxEmailLease(
        toInboxEmailDispatch(),
        requireNotNull(leaseOwner),
        requireNotNull(leaseToken),
        requireNotNull(leaseUntil).toInstant(),
    )
