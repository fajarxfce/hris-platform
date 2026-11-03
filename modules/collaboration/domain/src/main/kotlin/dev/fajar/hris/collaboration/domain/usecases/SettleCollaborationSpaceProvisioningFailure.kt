package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.resolveCollaborationSpaceProvisioningFailure
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceProvisioningRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.repositories.IdentityRepository
import java.time.Clock

/**
 * Metadata-only resolution after a failed advance transaction has released all guards. A current
 * lease is required; no native/content permission is granted and no provider/device keys change.
 */
class SettleCollaborationSpaceProvisioningFailure(
    private val provisioning: CollaborationSpaceProvisioningRepository,
    private val identities: IdentityRepository,
    private val journal: ChangeJournalRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(lease: CollaborationSpaceProvisioningLease, failure: Failure): Result<Boolean> {
        if (failure.code == "collaboration_provisioning_lease_lost") return Result.Success(false)
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
            val guard = identities.lockAccount(actor.accountId, shared = false)
            if (guard is Result.Failed) return@run guard
            val read =
                provisioning.metadata(
                    request.spaceId,
                    request.accountId,
                    request.deviceId,
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
            val decision =
                resolveCollaborationSpaceProvisioningFailure(lease, failure, clock.instant())
            val delay = decision.delaySeconds
            val applied =
                if (delay == null) provisioning.finish(lease, decision.failure)
                else provisioning.defer(lease, delay)
            if (applied is Result.Failed) return@run applied
            if (!(applied as Result.Success).value) return@run Result.Success(false)
            journal
                .record(
                    actor,
                    ChangeRecord(
                        "collaboration_space_device",
                        request.deviceId,
                        if (delay == null) "collaboration.space_provisioning_failed"
                        else "collaboration.space_provisioning_deferred",
                        buildMap {
                            put("spaceId", request.spaceId.toString())
                            put("provisioningRevision", request.revision.toString())
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
