package dev.fajar.hris.collaboration.data.mappers

import dev.fajar.hris.administration.domain.entities.ClientPlatform
import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.schema.tables.records.CollaborationSpaceProvisioningRecord
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit

fun spaceProvisioningFromRow(
    row: CollaborationSpaceProvisioningRecord
): CollaborationSpaceProvisioning {
    val request =
        CollaborationSpaceProvisioningRequest(
            requireNotNull(row.spaceId),
            requireNotNull(row.accountId),
            requireNotNull(row.deviceId),
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
    validateSpaceProvisioningRequest(request)
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
            CollaborationSpaceProvisioningOutcome(
                revision,
                CollaborationSpaceProvisioningState.valueOf(requireNotNull(row.lastOutcomeState)),
                requireNotNull(row.lastOutcomeAt).toInstant(),
                row.lastFailureCode?.let(CollaborationSpaceProvisioningFailure::valueOf),
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
            CollaborationSpaceProvisioningLease(
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
            .all { it.nano % 1000 == 0 }
    )
    return CollaborationSpaceProvisioning(
        request,
        created,
        available,
        state,
        attempts,
        lease,
        outcome,
    )
}

fun validateSpaceProvisioningRequest(request: CollaborationSpaceProvisioningRequest) {
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

fun spaceProvisioningRequestRow(
    request: CollaborationSpaceProvisioningRequest
): CollaborationSpaceProvisioningRecord {
    validateSpaceProvisioningRequest(request)
    return CollaborationSpaceProvisioningRecord().apply {
        spaceId = request.spaceId
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
        requestedAt = request.requestedAt.truncatedTo(ChronoUnit.MICROS).atOffset(UTC)
        expiresAt = request.expiresAt.truncatedTo(ChronoUnit.MICROS).atOffset(UTC)
        createdAt = requestedAt
        availableAt = requestedAt
        state = "QUEUED"
        attempts = 0
        require(expiresAt > requestedAt)
    }
}
