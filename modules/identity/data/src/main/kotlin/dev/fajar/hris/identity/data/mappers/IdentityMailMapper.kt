package dev.fajar.hris.identity.data.mappers

import dev.fajar.hris.identity.domain.entities.*
import dev.fajar.hris.schema.tables.records.IdentityMailDeliveriesRecord

fun IdentityMailDeliveriesRecord.toIdentityMailDelivery() =
    IdentityMailDelivery(
        challengeId,
        accountId,
        IdentityMailState.valueOf(state),
        attempts,
        availableAt.toInstant(),
        expiresAt.toInstant(),
        leaseUntil?.toInstant(),
        deliveredAt?.toInstant(),
        failureCode,
    )

fun IdentityMailDeliveriesRecord.toIdentityMailLease() =
    IdentityMailLease(
        challengeId,
        accountId,
        checkNotNull(leaseOwner),
        checkNotNull(leaseToken),
        attempts,
        expiresAt.toInstant(),
    )
