package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.util.UUID

/** A native setup read; it neither restarts expired work nor acquires provider credentials. */
class GetNativeCollaborationSpaceDeviceStatus(
    private val registry: CollaborationSpaceDeviceRepository,
    private val provisioning: CollaborationSpaceProvisioningRepository,
    private val gateway: CollaborationSpaceGatewaySessionRepository,
    private val spaces: CollaborationSpaceRepository,
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val policy: CollaborationSpacePolicy,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        spaceId: UUID,
        client: ClientVersion?,
    ): Result<CollaborationSpaceDeviceStatus?> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        val enabled = validateCollaborationSpaceClient(client, policy)
        if (enabled is Result.Failed) return enabled
        val started = clock.instant()
        return transactions.run(actor) {
            val guarded = identities.lockAccount(actor.accountId, shared = true)
            if (guarded is Result.Failed) return@run guarded
            val locked = sessions.lock(sessionId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val parent = (locked as Result.Success).value
            fun admission(): Result<CollaborationSpaceAdmission> {
                val acquired = identities.access(actor.accountId, null)
                if (acquired is Result.Failed) return acquired
                val current = spaces.current()
                if (current is Result.Failed) return current
                val at = clock.instant()
                if (at < started || at >= started.plusSeconds(30))
                    return Result.Failed(
                        Failure(FailureKind.UNAVAILABLE, "collaboration_admission_changed")
                    )
                return admitCollaborationSpaceSession(
                        actor,
                        (acquired as Result.Success).value,
                        parent,
                        sessionId,
                        sessionVersion,
                        (current as Result.Success).value,
                        client,
                        policy,
                        security,
                        at,
                    )
                    .flatMap { if (it.spaceId == spaceId) Result.Success(it) else denied() }
            }
            val admitted = admission()
            if (admitted is Result.Failed) return@run admitted
            val found = registry.findBound(spaceId, actor.accountId, sessionId)
            if (found is Result.Failed) return@run found
            val device = (found as Result.Success).value
            if (device == null) return@run admission().map { null }
            if (
                device.spaceId != spaceId ||
                    device.accountId != actor.accountId ||
                    device.originNativeSessionId != sessionId ||
                    device.platform.name != client?.platform?.name
            )
                return@run denied()
            // The account guard excludes device publication/retry. Queue-only claims may change
            // the operational head; this response describes its observed revision, not a grant.
            val work = provisioning.metadata(spaceId, actor.accountId, device.id)
            if (work is Result.Failed) return@run work
            val head =
                (work as Result.Success).value
                    ?: return@run Result.Failed(
                        Failure(FailureKind.UNEXPECTED, "invalid_collaboration_provisioning")
                    )
            val observedGateway = gateway.find(spaceId, actor.accountId, device.id)
            if (observedGateway is Result.Failed) return@run observedGateway
            admission().flatMap { current ->
                collaborationSpaceDeviceStatus(
                    device,
                    head,
                    current.admittedAt,
                    (observedGateway as Result.Success).value,
                )
            }
        }
    }

    private fun denied() =
        Result.Failed(Failure(FailureKind.FORBIDDEN, "collaboration_access_denied"))
}
