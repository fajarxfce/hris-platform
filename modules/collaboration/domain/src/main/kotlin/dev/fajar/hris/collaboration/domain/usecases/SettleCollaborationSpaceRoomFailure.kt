package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.resolveCollaborationSpaceRoomFailure
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceRoomRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.Clock

/**
 * Metadata-only resolution after a failed advance transaction has released all guards. A current
 * lease is required; no native/content permission is granted and no provider/device keys change.
 */
class SettleCollaborationSpaceRoomFailure(
    private val rooms: CollaborationSpaceRoomRepository,
    private val identities: IdentityRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(lease: CollaborationSpaceRoomLease, failure: Failure): Result<Boolean> {
        if (failure.code == "collaboration_room_lease_lost") return Result.Success(false)
        val request = lease.request
        val actor =
            Actor(
                request.accountId,
                null,
                emptySet(),
                request.authenticatedAt,
                request.correlationId,
                mfaVerifiedAt = request.mfaVerifiedAt,
                credentialVersion = request.credentialVersion,
            )
        return transactions.run(actor) {
            // Match native/worker lock ordering even though this command changes only metadata.
            val roomGuard = rooms.lock(request.spaceId, request.conversationId)
            if (roomGuard is Result.Failed) return@run roomGuard
            val guard = identities.lockAccount(actor.accountId, shared = true)
            if (guard is Result.Failed) return@run guard
            val read =
                rooms.provisioning(
                    request.spaceId,
                    request.accountId,
                    request.conversationId,
                    lock = true,
                )
            if (read is Result.Failed) return@run read
            val head = (read as Result.Success).value
            if (
                head == null ||
                    head.lease != lease ||
                    head.state != CollaborationSpaceProvisioningState.RUNNING
            )
                return@run Result.Success(false)
            val decision = resolveCollaborationSpaceRoomFailure(lease, failure, clock.instant())
            val delay = decision.delaySeconds
            val applied =
                if (delay == null) rooms.finish(lease, decision.failure)
                else rooms.defer(lease, delay)
            if (applied is Result.Failed) return@run applied
            if (!(applied as Result.Success).value) return@run Result.Success(false)
            journal
                .record(
                    actor,
                    ChangeRecord(
                        "collaboration_space_room",
                        request.conversationId,
                        if (delay == null) "collaboration.space_room_preparation_failed"
                        else "collaboration.space_room_preparation_deferred",
                        buildMap {
                            put("spaceId", request.spaceId.toString())
                            put("requestRevision", request.revision.toString())
                            put("attempt", lease.attempt.toString())
                            put("failure", decision.failure.name)
                            if (delay != null) put("retryAfterSeconds", delay.toString())
                        },
                    ),
                )
                .map { true }
        }
    }
}
