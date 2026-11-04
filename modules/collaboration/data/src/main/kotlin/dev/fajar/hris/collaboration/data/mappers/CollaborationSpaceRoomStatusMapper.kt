package dev.fajar.hris.collaboration.data.mappers

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.schema.tables.records.CollaborationSpaceRoomStatusRecord
import java.util.UUID

fun CollaborationSpaceRoomStatusRecord.toSpaceRoomStatus(
    space: UUID,
    conversation: UUID,
): CollaborationSpaceRoomStatus {
    requireSpaceRoomIds(space, conversation)
    require(spaceId == space && conversationId == conversation)
    val account = requireNotNull(accountId)
    requireSpaceRoomIds(account)
    val revision = requireNotNull(this.revision)
    val state = CollaborationSpaceProvisioningState.valueOf(requireNotNull(this.state))
    val attempts = requireNotNull(this.attempts)
    val requested = requireNotNull(requestedAt).toInstant()
    val available = requireNotNull(availableAt).toInstant()
    val expires = requireNotNull(expiresAt).toInstant()
    val completed = completedAt?.toInstant()
    val failure = failureCode?.let(CollaborationSpaceRoomFailure::valueOf)
    val last =
        if (
            lastOutcomeRevision == null &&
                lastOutcomeState == null &&
                lastOutcomeAt == null &&
                lastFailureCode == null
        )
            null
        else
            CollaborationSpaceRoomOutcome(
                requireNotNull(lastOutcomeRevision),
                CollaborationSpaceProvisioningState.valueOf(requireNotNull(lastOutcomeState)),
                requireNotNull(lastOutcomeAt).toInstant(),
                lastFailureCode?.let(CollaborationSpaceRoomFailure::valueOf),
            )
    listOfNotNull(requested, available, expires, completed, last?.at)
        .forEach(::requireSpaceRoomTime)
    require(revision in 0..999999 && attempts in 0..8)
    require(
        expires > requested &&
            expires <= requested.plusSeconds(300) &&
            available in requested..expires
    )
    require(state != CollaborationSpaceProvisioningState.QUEUED || attempts < 8)
    require(
        state !in
            setOf(
                CollaborationSpaceProvisioningState.RUNNING,
                CollaborationSpaceProvisioningState.SUCCEEDED,
            ) || attempts > 0
    )
    if (last == null) require(revision == 0L)
    else {
        require(last.revision in 0..revision)
        require(
            last.state in
                setOf(
                    CollaborationSpaceProvisioningState.SUCCEEDED,
                    CollaborationSpaceProvisioningState.FAILED,
                    CollaborationSpaceProvisioningState.CANCELLED,
                )
        )
        require(
            (last.state == CollaborationSpaceProvisioningState.FAILED) == (last.failure != null)
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
        requireNotNull(last)
        require(
            last.revision == revision &&
                last.state == state &&
                last.at == completed &&
                last.at >= requested &&
                last.failure == failure
        )
    } else {
        require(completed == null && failure == null)
        require(last == null || (last.revision < revision && last.at <= requested))
    }
    return CollaborationSpaceRoomStatus(
        space,
        conversation,
        revision,
        state,
        attempts,
        requested,
        available,
        expires,
        completed,
        failure,
        last,
        account,
    )
}
