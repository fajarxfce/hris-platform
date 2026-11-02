package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationRegistryRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.NativeSession
import dev.fajar.hris.identity.domain.policies.*
import dev.fajar.hris.identity.domain.repositories.*
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Discovers this native parent's retained device references, independently of company selection.
 * Like own-session metadata, this grants neither company admission nor access to chat content.
 */
class ListNativeCollaborationDevices(
    private val registry: CollaborationRegistryRepository,
    private val sessions: NativeSessionRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val service: CollaborationServicePolicy,
) {
    fun execute(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        afterCompanyId: UUID?,
        limit: Int,
    ): Result<CollaborationNativeDevicePage> {
        if (actor.companyId != null)
            return Result.Failed(Failure(FailureKind.FORBIDDEN, "platform_scope_required"))
        if (limit !in 1..32)
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_collaboration_page"))
        if (!service.enabled)
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        val started = clock.instant()
        return transactions.run(actor) {
            interrupted()
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val acquired = identities.access(actor.accountId, null)
            if (acquired is Result.Failed) return@run acquired
            val access = (acquired as Result.Success).value
            val account = validatePlatformCommandActor(actor, access)
            if (account is Result.Failed) return@run account
            val locked = sessions.lock(sessionId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val parent = (locked as Result.Success).value
            fun guard(): Result<Unit> {
                interrupted()
                val now = clock.instant()
                if (now.isBefore(started) || !now.isBefore(started.plusSeconds(30)))
                    return Result.Failed(
                        Failure(FailureKind.UNAVAILABLE, "collaboration_directory_timeout")
                    )
                val current = validatePlatformCommandActor(actor, access)
                if (current is Result.Failed) return current
                return admitParent(actor, parent, sessionId, sessionVersion, now)
            }
            guard().let { if (it is Result.Failed) return@run it }
            val found = registry.listBound(actor.accountId, sessionId, afterCompanyId, limit + 1)
            guard().let { if (it is Result.Failed) return@run it }
            if (found is Result.Failed) return@run found
            val devices = (found as Result.Success).value
            val now = clock.instant()
            if (
                devices.size > limit + 1 ||
                    devices.any {
                        it.accountId != actor.accountId ||
                            it.nativeSessionId != sessionId ||
                            it.version < 0 ||
                            it.createdAt.isAfter(now) ||
                            (afterCompanyId != null &&
                                it.companyId.toString() <= afterCompanyId.toString())
                    } ||
                    devices.zipWithNext().any { (a, b) ->
                        a.companyId.toString() >= b.companyId.toString()
                    }
            )
                return@run Result.Failed(
                    Failure(FailureKind.UNEXPECTED, "invalid_collaboration_directory")
                )
            val page =
                devices.take(limit).map {
                    CollaborationNativeDeviceReference(
                        it.companyId,
                        it.id,
                        it.version,
                        it.state,
                        it.platform,
                    )
                }
            guard().let { if (it is Result.Failed) return@run it }
            Result.Success(
                CollaborationNativeDevicePage(
                    actor.accountId,
                    sessionId,
                    page,
                    if (devices.size > limit) page.last().companyId else null,
                )
            )
        }
    }

    private fun admitParent(
        actor: Actor,
        parent: NativeSession?,
        sessionId: UUID,
        version: Long,
        at: Instant,
    ): Result<Unit> =
        if (
            parent != null &&
                parent.id == sessionId &&
                parent.accountId == actor.accountId &&
                parent.credentialVersion == actor.credentialVersion &&
                parent.version == version &&
                version >= 0 &&
                nativeSessionActive(parent, at) &&
                parent.accessExpiresAt.isAfter(at)
        )
            Result.Success(Unit)
        else nativeAuthenticationRequired()

    private fun interrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
    }
}
