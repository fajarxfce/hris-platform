package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.*
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.collaboration.domain.entities.CollaborationAdmission
import dev.fajar.hris.collaboration.domain.entities.CollaborationServicePolicy
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.collaboration.domain.repositories.CollaborationRegistryRepository
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.nativeAuthenticationRequired
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.util.UUID

/** Acquires a short-lived observation, not a provider credential or an offline-store lease. */
class AcquireNativeCollaborationAdmission(
    private val registry: CollaborationRegistryRepository,
    private val sessions: NativeSessionRepository,
    private val policies: CompanyClientPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val clock: Clock,
    private val service: CollaborationServicePolicy,
) {
    fun execute(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        clientVersion: ClientVersion?,
    ): Result<CollaborationAdmission> {
        val permission = actor.requirePermission(COLLABORATION_CHAT_PERMISSION)
        if (permission is Result.Failed) return permission
        if (!service.enabled)
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val policyGuard = policies.lock(company, shared = true)
            if (policyGuard is Result.Failed) return@run policyGuard
            val companyGuard = companies.lock(company, shared = true)
            if (companyGuard is Result.Failed) return@run companyGuard
            val memberGuard = members.lock(company, shared = true)
            if (memberGuard is Result.Failed) return@run memberGuard
            val accountGuard = identities.lockAccount(actor.accountId, shared = true)
            if (accountGuard is Result.Failed) return@run accountGuard
            val locked = sessions.lock(sessionId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val session = (locked as Result.Success).value
            if (session?.id != sessionId) return@run nativeAuthenticationRequired()
            val acquired = identities.access(actor.accountId, company)
            if (acquired is Result.Failed) return@run acquired
            val access = (acquired as Result.Success).value
            val allowed =
                collaborationParentDeadline(
                    actor,
                    access,
                    session,
                    sessionVersion,
                    clock.instant(),
                    security,
                )
            if (allowed is Result.Failed) return@run allowed
            val found = registry.findBound(company, actor.accountId, sessionId)
            if (found is Result.Failed) return@run found
            val device = (found as Result.Success).value
            if (
                device != null &&
                    clientVersion != null &&
                    device.platform.name != clientVersion.platform.name
            )
                return@run Result.Failed(
                    Failure(FailureKind.VALIDATION, "invalid_collaboration_device")
                )
            val now = clock.instant()
            val effective = policies.effective(company, now)
            if (effective is Result.Failed) return@run effective
            // A scheduled policy can activate while waiting on I/O. Re-evaluate before return.
            val evaluatedAt = clock.instant()
            nativeCollaborationClientDeadline(
                    (effective as Result.Success).value,
                    clientVersion,
                    evaluatedAt,
                )
                .flatMap { deadline ->
                    admitCollaborationDevice(
                            actor,
                            access,
                            session,
                            sessionVersion,
                            device,
                            evaluatedAt,
                            security,
                        )
                        .map { it.copy(expiresAt = minOf(it.expiresAt, deadline)) }
                }
        }
    }
}
