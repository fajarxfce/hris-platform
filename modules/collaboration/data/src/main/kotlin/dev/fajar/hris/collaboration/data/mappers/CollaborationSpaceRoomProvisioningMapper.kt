package dev.fajar.hris.collaboration.data.mappers

import dev.fajar.hris.administration.domain.entities.ClientPlatform
import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.schema.tables.records.CollaborationSpaceRoomProvisioningRecord
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.util.UUID

fun spaceRoomProvisioningFromRow(
    row: CollaborationSpaceRoomProvisioningRecord
): CollaborationSpaceRoomProvisioning {
    val request =
        CollaborationSpaceRoomRequest(
            requireNotNull(row.spaceId),
            requireNotNull(row.conversationId),
            requireNotNull(row.accountId),
            requireNotNull(row.deviceId),
            requireNotNull(row.deviceVersion),
            requireNotNull(row.providerGeneration),
            requireNotNull(row.nativeSessionId),
            requireNotNull(row.revision),
            requireNotNull(row.nativeSessionVersion),
            requireNotNull(row.credentialVersion),
            requireNotNull(row.authenticatedAt).toInstant(),
            row.mfaVerifiedAt?.toInstant(),
            requireNotNull(row.correlationId),
            ClientVersion(
                ClientPlatform.valueOf(requireNotNull(row.clientPlatform)),
                requireNotNull(row.clientBuild).also { require(it in 1..999999999) }.toInt(),
            ),
            requireNotNull(row.requestedAt).toInstant(),
            requireNotNull(row.expiresAt).toInstant(),
        )
    validateSpaceRoomRequest(request)
    val created = requireNotNull(row.createdAt).toInstant()
    val available = requireNotNull(row.availableAt).toInstant()
    val state = CollaborationSpaceProvisioningState.valueOf(requireNotNull(row.state))
    val attempts = requireNotNull(row.attempts)
    require(
        created <= request.requestedAt &&
            available >= request.requestedAt &&
            available <= request.expiresAt
    )
    require(
        attempts in 0..8 && (state != CollaborationSpaceProvisioningState.QUEUED || attempts < 8)
    )
    val outcome =
        row.lastOutcomeRevision?.let { revision ->
            CollaborationSpaceRoomOutcome(
                revision,
                CollaborationSpaceProvisioningState.valueOf(requireNotNull(row.lastOutcomeState)),
                requireNotNull(row.lastOutcomeAt).toInstant(),
                row.lastFailureCode?.let(CollaborationSpaceRoomFailure::valueOf),
            )
        }
    if (outcome == null)
        require(
            row.lastOutcomeState == null &&
                row.lastOutcomeAt == null &&
                row.lastFailureCode == null &&
                request.revision == 0L
        )
    else {
        require(outcome.revision in 0..request.revision && outcome.at >= created)
        require(
            outcome.state in
                setOf(
                    CollaborationSpaceProvisioningState.SUCCEEDED,
                    CollaborationSpaceProvisioningState.FAILED,
                    CollaborationSpaceProvisioningState.CANCELLED,
                )
        )
        require(
            (outcome.state == CollaborationSpaceProvisioningState.FAILED) ==
                (outcome.failure != null)
        )
    }
    val terminal =
        state in
            setOf(
                CollaborationSpaceProvisioningState.SUCCEEDED,
                CollaborationSpaceProvisioningState.FAILED,
                CollaborationSpaceProvisioningState.CANCELLED,
            )
    if (terminal) {
        require(
            outcome != null &&
                outcome.revision == request.revision &&
                outcome.state == state &&
                outcome.at >= request.requestedAt
        )
        require(
            row.completedAt?.toInstant() == outcome.at && row.failureCode == outcome.failure?.name
        )
        require(state != CollaborationSpaceProvisioningState.SUCCEEDED || attempts > 0)
    } else {
        require(row.completedAt == null && row.failureCode == null)
        require(
            outcome == null ||
                (outcome.revision < request.revision && outcome.at <= request.requestedAt)
        )
    }
    val lease =
        if (state == CollaborationSpaceProvisioningState.RUNNING) {
            val until = requireNotNull(row.leaseUntil).toInstant()
            require(attempts > 0 && until > request.requestedAt && until <= request.expiresAt)
            requireSpaceRoomIds(requireNotNull(row.leaseOwner), requireNotNull(row.leaseToken))
            CollaborationSpaceRoomLease(
                request,
                attempts,
                requireNotNull(row.leaseOwner),
                requireNotNull(row.leaseToken),
                until,
            )
        } else {
            require(row.leaseOwner == null && row.leaseToken == null && row.leaseUntil == null)
            null
        }
    require(
        listOfNotNull(
                created,
                available,
                request.requestedAt,
                request.expiresAt,
                outcome?.at,
                lease?.until,
            )
            .all { it.nano % 1000 == 0 && it.epochSecond in -62135596800L..253402300799L }
    )
    return CollaborationSpaceRoomProvisioning(
        request,
        created,
        available,
        state,
        attempts,
        lease,
        outcome,
    )
}

fun requireSpaceRoomTime(at: Instant) {
    require(at.nano % 1000 == 0 && at.epochSecond in -62135596800L..253402300799L)
}

fun requireSpaceRoomIds(vararg ids: UUID) {
    require(ids.none { it == UUID(0, 0) })
}

fun validateSpaceRoomRequest(request: CollaborationSpaceRoomRequest) {
    requireSpaceRoomIds(
        request.spaceId,
        request.conversationId,
        request.accountId,
        request.deviceId,
        request.nativeSessionId,
        request.correlationId,
    )
    require(request.deviceVersion in 0..999999 && request.providerGeneration in 0..999999)
    listOfNotNull(
            request.requestedAt,
            request.expiresAt,
            request.authenticatedAt,
            request.mfaVerifiedAt,
        )
        .forEach(::requireSpaceRoomTime)

    require(
        request.revision in 0..999999 &&
            request.nativeSessionVersion >= 0 &&
            request.credentialVersion >= 0
    )
    require(
        request.client.platform in setOf(ClientPlatform.ANDROID, ClientPlatform.IOS) &&
            request.client.build in 1..999999999
    )
    require(
        request.authenticatedAt <= request.requestedAt &&
            request.mfaVerifiedAt?.let { it <= request.requestedAt } != false
    )
    require(
        request.expiresAt > request.requestedAt &&
            request.expiresAt <= request.requestedAt.plusSeconds(300)
    )
    require(
        listOfNotNull(request.authenticatedAt, request.mfaVerifiedAt).all { it.nano % 1000 == 0 }
    )
}

fun spaceRoomRequestRow(
    request: CollaborationSpaceRoomRequest
): CollaborationSpaceRoomProvisioningRecord {
    validateSpaceRoomRequest(request)
    return CollaborationSpaceRoomProvisioningRecord().apply {
        spaceId = request.spaceId
        conversationId = request.conversationId
        deviceVersion = request.deviceVersion
        providerGeneration = request.providerGeneration
        accountId = request.accountId
        deviceId = request.deviceId
        nativeSessionId = request.nativeSessionId
        revision = request.revision
        nativeSessionVersion = request.nativeSessionVersion
        credentialVersion = request.credentialVersion
        authenticatedAt = request.authenticatedAt.atOffset(UTC)
        mfaVerifiedAt = request.mfaVerifiedAt?.atOffset(UTC)
        correlationId = request.correlationId
        clientPlatform = request.client.platform.name
        clientBuild = request.client.build.toLong()
        requestedAt = request.requestedAt.atOffset(UTC)
        expiresAt = request.expiresAt.atOffset(UTC)
        createdAt = requestedAt
        availableAt = requestedAt
        state = "QUEUED"
        attempts = 0
        require(expiresAt > requestedAt)
    }
}
