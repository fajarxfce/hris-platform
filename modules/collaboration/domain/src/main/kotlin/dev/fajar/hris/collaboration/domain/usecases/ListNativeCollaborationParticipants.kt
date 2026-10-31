package dev.fajar.hris.collaboration.domain.usecases

import dev.fajar.hris.administration.domain.entities.ClientPlatform
import dev.fajar.hris.administration.domain.entities.ClientVersion
import dev.fajar.hris.administration.domain.repositories.CompanyClientPolicyRepository
import dev.fajar.hris.collaboration.domain.entities.*
import dev.fajar.hris.collaboration.domain.policies.*
import dev.fajar.hris.core.domain.*
import dev.fajar.hris.identity.domain.entities.IdentitySecurityPolicy
import dev.fajar.hris.identity.domain.policies.nativeAuthenticationRequired
import dev.fajar.hris.identity.domain.repositories.*
import dev.fajar.hris.organization.domain.repositories.CompanyRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Company contact discovery does not allocate a provider device or grant room access. */
class ListNativeCollaborationParticipants(
    private val sessions: NativeSessionRepository,
    private val policies: CompanyClientPolicyRepository,
    private val companies: CompanyRepository,
    private val members: MembershipRepository,
    private val identities: IdentityRepository,
    private val transactions: TransactionRunner,
    private val security: IdentitySecurityPolicy,
    private val service: CollaborationServicePolicy,
    private val provider: CollaborationMatrixPolicy,
    private val clock: Clock,
) {
    fun execute(
        actor: Actor,
        sessionId: UUID,
        sessionVersion: Long,
        client: ClientVersion?,
        query: String,
        after: UUID?,
        limit: Int,
    ): Result<Page<CollaborationParticipantReference>> {
        val permission = actor.requirePermission(COLLABORATION_CHAT_PERMISSION)
        if (permission is Result.Failed) return permission
        if (!service.enabled || !provider.enabled)
            return Result.Failed(Failure(FailureKind.UNAVAILABLE, "collaboration_unavailable"))
        if (
            client == null ||
                client.platform !in setOf(ClientPlatform.ANDROID, ClientPlatform.IOS) ||
                client.build <= 0
        )
            return Result.Failed(Failure(FailureKind.VALIDATION, "invalid_client_version"))
        if (query.length > 80 || query.any(Char::isISOControl) || limit !in 1..50)
            return Result.Failed(
                Failure(FailureKind.VALIDATION, "invalid_collaboration_participant_query")
            )
        val company =
            actor.companyId
                ?: return Result.Failed(Failure(FailureKind.FORBIDDEN, "company_required"))
        return transactions.run(actor) {
            val pg = policies.lock(company, shared = true)
            if (pg is Result.Failed) return@run pg
            val cg = companies.lock(company, shared = true)
            if (cg is Result.Failed) return@run cg
            val mg = members.lock(company, shared = true)
            if (mg is Result.Failed) return@run mg
            val ag = identities.lockAccount(actor.accountId, shared = true)
            if (ag is Result.Failed) return@run ag
            val locked = sessions.lock(sessionId, actor.accountId)
            if (locked is Result.Failed) return@run locked
            val parent = (locked as Result.Success).value
            if (parent?.id != sessionId) return@run nativeAuthenticationRequired()
            val acquired = identities.access(actor.accountId, company)
            if (acquired is Result.Failed) return@run acquired
            val access = (acquired as Result.Success).value
            fun admission(): Result<Instant> {
                val effective = policies.effective(company, clock.instant())
                if (effective is Result.Failed) return effective
                val at = clock.instant()
                return nativeCollaborationClientDeadline(
                        (effective as Result.Success).value,
                        client,
                        at,
                    )
                    .flatMap { deadline ->
                        collaborationParentDeadline(
                                actor,
                                access,
                                parent,
                                sessionVersion,
                                at,
                                security,
                            )
                            .map { minOf(it, deadline) }
                    }
            }
            val admitted = admission()
            if (admitted is Result.Failed) return@run admitted
            members
                .activeReferences(
                    company,
                    setOf(COLLABORATION_CHAT_PERMISSION),
                    query.trim(),
                    after,
                    limit,
                    excludedAccountId = actor.accountId,
                )
                .flatMap { page ->
                    admission().map {
                        Page(
                            page.items.map {
                                CollaborationParticipantReference(it.id, it.displayName)
                            },
                            page.nextCursor,
                        )
                    }
                }
        }
    }
}
