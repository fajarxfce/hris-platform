package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.validCollaborationSpaceServer
import dev.fajar.hris.collaboration.domain.repositories.CollaborationSpaceProvisioningRepository
import dev.fajar.hris.core.domain.*
import java.util.UUID

/** Single atomic worker metadata statement; complete it before any native admission transaction. */
class ClaimCollaborationSpaceProvisioning(
    private val provisioning: CollaborationSpaceProvisioningRepository,
    private val policy: CollaborationSpacePolicy,
    private val provider: CollaborationMatrixPolicy,
) {
    fun execute(
        ownerId: UUID,
        limit: Int,
        leaseSeconds: Int = 60,
    ): Result<List<CollaborationSpaceProvisioningLease>> {
        if (!policy.enabled || !provider.enabled) return Result.Success(emptyList())
        if (!validCollaborationSpaceServer(provider.serverName))
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        if (limit !in 1..8 || leaseSeconds !in 1..60)
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_provisioning_bounds")
            )
        return provisioning.claim(ownerId, limit, leaseSeconds).flatMap { heads ->
            if (heads.size > limit)
                return@flatMap Result.Failed(
                    Failure(FailureKind.UNEXPECTED, "invalid_collaboration_provisioning")
                )
            val leases = heads.mapNotNull { it.lease }
            if (
                leases.size != heads.size ||
                    heads.any { head ->
                        val lease = head.lease
                        lease == null ||
                            head.state != CollaborationSpaceProvisioningState.RUNNING ||
                            head.request != lease.request ||
                            head.attempts != lease.attempt
                    } ||
                    leases.any { it.ownerId != ownerId || it.attempt !in 1..8 } ||
                    leases.map { it.token }.distinct().size != leases.size ||
                    leases.map { it.request.spaceId to it.request.deviceId }.distinct().size !=
                        leases.size
            )
                Result.Failed(Failure(FailureKind.UNEXPECTED, "invalid_collaboration_provisioning"))
            else Result.Success(leases)
        }
    }
}
